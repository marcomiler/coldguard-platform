package com.coldguard.incident.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class IncidentRepositoryAdapterIdTest {

  private final IncidentJpaRepository jpa = mock(IncidentJpaRepository.class);
  private final IncidentRepositoryAdapter adapter = new IncidentRepositoryAdapter(jpa);

  @Test
  void findById_idThatIsNotAUuid_isNotFoundWithoutTouchingTheDatabase() {
    for (String id : new String[] {"x", "", "not-a-uuid", "123", " "}) {
      assertThat(adapter.findById(id)).as("id '%s'", id).isEmpty();
    }
    verifyNoInteractions(jpa);
  }
}
