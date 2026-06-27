package com.els.promsync.repository;

import com.els.promsync.entity.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {
}
