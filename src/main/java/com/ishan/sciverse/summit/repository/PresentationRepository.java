package com.ishan.sciverse.summit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.entity.Session;

import java.util.List;

@Repository
public interface PresentationRepository extends JpaRepository<Presentation, Long> {
	List<Presentation> findAllByOrderByIdDesc();
	List<Presentation> findBySession(Session session);
	List<Presentation> findBySessionOrderByIdAsc(Session session);
}
