package com.moyeobom.guest.repository;

import com.moyeobom.guest.domain.GuestSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestSettingRepository extends JpaRepository<GuestSetting, Long> {
}
