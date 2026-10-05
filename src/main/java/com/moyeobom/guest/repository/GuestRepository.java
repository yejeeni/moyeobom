package com.moyeobom.guest.repository;

import com.moyeobom.guest.domain.Guest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    Optional<Guest> findByPublicId(String publicId);
}
