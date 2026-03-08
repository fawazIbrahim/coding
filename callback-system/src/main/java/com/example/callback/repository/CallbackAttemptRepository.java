package com.example.callback.repository;

import com.example.callback.domain.CallbackAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CallbackAttemptRepository extends JpaRepository<CallbackAttempt, UUID> {

    List<CallbackAttempt> findByExecutionIdOrderByAttemptNumberAsc(UUID executionId);
}
