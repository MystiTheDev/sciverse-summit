package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.ResolutionDraft;
import com.ishan.sciverse.summit.entity.ResolutionDraftFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResolutionDraftFileRepository extends JpaRepository<ResolutionDraftFile, Long> {
    List<ResolutionDraftFile> findByResolutionOrderByIdAsc(ResolutionDraft resolution);
}
