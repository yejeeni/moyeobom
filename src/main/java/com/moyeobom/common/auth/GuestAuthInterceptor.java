package com.moyeobom.common.auth;

import com.moyeobom.guest.service.GuestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class GuestAuthInterceptor implements HandlerInterceptor {

    public static final String GUEST_HEADER = "X-Guest-Id";
    static final String GUEST_ID_ATTRIBUTE = GuestAuthInterceptor.class.getName() + ".guestId";

    private final GuestService guestService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        Long guestId = guestService.authenticate(request.getHeader(GUEST_HEADER));
        request.setAttribute(GUEST_ID_ATTRIBUTE, guestId);
        return true;
    }
}
