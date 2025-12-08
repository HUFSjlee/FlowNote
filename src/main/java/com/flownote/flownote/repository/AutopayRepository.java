package com.flownote.flownote.repository;

import com.flownote.flownote.entity.Autopay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutopayRepository extends JpaRepository<Autopay, Long> {
}
