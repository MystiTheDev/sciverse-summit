package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.ResolutionDraft;
import com.ishan.sciverse.summit.entity.Session;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResolutionDraftRepository extends JpaRepository<ResolutionDraft, Long> {

    List<ResolutionDraft> findBySessionOrderByIdDesc(Session session);

    @EntityGraph(attributePaths = "files")
    Optional<ResolutionDraft> findWithFilesById(Long id);
}
