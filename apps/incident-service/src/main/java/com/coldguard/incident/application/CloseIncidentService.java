package com.coldguard.incident.application;

import com.coldguard.incident.domain.Incident;
import org.springframework.stereotype.Service;

@Service
public class CloseIncidentService {

    private final IncidentRepository incidentRepository;

    public CloseIncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public Incident close(CloseIncidentCommand command) {
        Incident incident = incidentRepository.findById(command.incidentId())
                .orElseThrow(() -> new IncidentNotFoundException(command.incidentId()));

        Incident closed = incident.close();
        incidentRepository.update(closed);
        return closed;
    }
}
