package com.example.alerting.channel;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link ChannelSetting} rows, keyed by channel type code. */
public interface ChannelSettingRepository extends JpaRepository<ChannelSetting, String> {
}
