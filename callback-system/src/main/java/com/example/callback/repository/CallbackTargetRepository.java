package com.example.callback.repository;

import com.example.callback.domain.CallbackTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallbackTargetRepository extends JpaRepository<CallbackTarget, UUID> {

    Optional<CallbackTarget> findByNameAndEnabledTrue(String name);
}
