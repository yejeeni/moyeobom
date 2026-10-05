package com.moyeobom.guest.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.common.time.Times;
import com.moyeobom.guest.domain.Guest;
import com.moyeobom.guest.domain.GuestSetting;
import com.moyeobom.guest.dto.GuestDtos.SettingResponse;
import com.moyeobom.guest.dto.GuestDtos.SettingUpdateRequest;
import com.moyeobom.guest.repository.GuestRepository;
import com.moyeobom.guest.repository.GuestSettingRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;
    private final GuestSettingRepository guestSettingRepository;
    private final Clock clock;

    @Transactional
    public Guest issue() {
        Guest guest = guestRepository.save(Guest.issue(Times.now(clock)));
        guestSettingRepository.save(GuestSetting.defaultOf(guest.getId()));
        return guest;
    }

    /**
     * X-Guest-Id 값으로 게스트를 찾아 내부 id를 돌려준다. 없거나 형식이 틀리면 401.
     */
    @Transactional
    public Long authenticate(String publicId) {
        Guest guest = findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUEST_NOT_FOUND));
        guest.touch(Times.now(clock));
        return guest.getId();
    }

    @Transactional(readOnly = true)
    public Optional<Long> findIdByPublicId(String publicId) {
        return findByPublicId(publicId).map(Guest::getId);
    }

    @Transactional(readOnly = true)
    public Guest getGuest(Long guestId) {
        return guestRepository.findById(guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUEST_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public SettingResponse getSetting(Long guestId) {
        GuestSetting setting = findSetting(guestId);
        return new SettingResponse(setting.isBreakAlertEnabled(), setting.getBreakAlertMinutes());
    }

    @Transactional
    public SettingResponse changeSetting(Long guestId, SettingUpdateRequest request) {
        GuestSetting setting = findSetting(guestId);
        setting.change(request.breakAlertEnabled(), request.breakAlertMinutes());
        return new SettingResponse(setting.isBreakAlertEnabled(), setting.getBreakAlertMinutes());
    }

    private GuestSetting findSetting(Long guestId) {
        return guestSettingRepository.findById(guestId)
                .orElseGet(() -> guestSettingRepository.save(GuestSetting.defaultOf(guestId)));
    }

    private Optional<Guest> findByPublicId(String publicId) {
        if (publicId == null || !isUuid(publicId)) {
            return Optional.empty();
        }
        return guestRepository.findByPublicId(publicId);
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
