package com.example.alerting.web;

import com.example.alerting.identity.CurrentUser;
import com.example.alerting.identity.IdentityResolver;
import com.example.alerting.identity.RequiresRole;
import com.example.alerting.identity.RoleGuard;
import com.example.alerting.shared.DomainException;
import com.example.alerting.shared.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Identifies the caller of every {@code /api/v1} request and enforces {@link RequiresRole} (docs/02,
 * section 9: missing identity gives 401, wrong role gives 403).
 *
 * <p>Identification is delegated to the {@link IdentityResolver} port, so replacing the demo header with
 * real authentication does not change this class. The identified user is stored as a request attribute for
 * {@link CurrentUserArgumentResolver}. Failures are thrown as {@link DomainException}s and rendered by the
 * problem handler like any other error.
 */
public class IdentityInterceptor implements HandlerInterceptor {

    /** The request attribute that holds the identified {@link CurrentUser}. */
    static final String CURRENT_USER_ATTRIBUTE = IdentityInterceptor.class.getName() + ".CURRENT_USER";

    private final IdentityResolver resolver;

    /**
     * Creates the interceptor.
     *
     * @param resolver identifies callers
     */
    public IdentityInterceptor(IdentityResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUser user = resolver.resolve(request::getHeader).orElseThrow(() -> new DomainException(
                DomainException.Kind.UNAUTHENTICATED, ErrorCodes.UNAUTHENTICATED, "Authentication is required."));
        request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
        if (handler instanceof HandlerMethod method) {
            RequiresRole required = requiredRole(method);
            if (required != null) {
                RoleGuard.require(user, required.value());
            }
        }
        return true;
    }

    /** The annotation on the method wins over the one on the class. */
    private static RequiresRole requiredRole(HandlerMethod method) {
        RequiresRole onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresRole.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresRole.class);
    }
}
