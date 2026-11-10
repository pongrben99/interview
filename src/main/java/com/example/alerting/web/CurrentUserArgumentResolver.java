package com.example.alerting.web;

import com.example.alerting.identity.CurrentUser;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a controller method declare a {@link CurrentUser} parameter and receive the identified caller.
 *
 * <p>The user was identified by {@link IdentityInterceptor}, which covers every {@code /api/v1} path. A
 * controller outside that path asking for a {@code CurrentUser} is a programming error and fails loudly
 * instead of running without a caller.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(IdentityInterceptor.CURRENT_USER_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        if (user == null) {
            throw new IllegalStateException(
                    "No current user: the identity interceptor did not run for this path, which must be under /api/v1");
        }
        return user;
    }
}
