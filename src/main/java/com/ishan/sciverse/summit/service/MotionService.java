package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.Motion;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.MotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MotionService {

    @Autowired
    private MotionRepository motionRepository;

    public Motion raise(Session session, String type, String proposer) {
        return raise(session, type, null, proposer);
    }

    public Motion raise(Session session, String type, String detail, String proposer) {
        Motion motion = new Motion();
        motion.setSession(session);
        motion.setType(type);
        motion.setDetail(detail != null && !detail.isBlank() ? detail.trim() : null);
        motion.setProposer(proposer);
        motion.setStatus("PENDING");
        return motionRepository.save(motion);
    }

    public List<Motion> listBySession(Session session) {
        return motionRepository.findBySessionOrderByIdDesc(session);
    }

    public List<Motion> listLiveBySession(Session session) {
        return motionRepository.findBySessionAndStatusOrderByIdDesc(session, "PENDING");
    }

    public Optional<Motion> getById(Long id) {
        return motionRepository.findById(id);
    }

    public void updateStatus(Long id, String status) {
        motionRepository.findById(id).ifPresent(m -> {
            m.setStatus(status);
            motionRepository.save(m);
        });
    }
}
