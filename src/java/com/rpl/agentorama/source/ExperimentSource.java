package com.rpl.agentorama.source;

import java.util.UUID;

public interface ExperimentSource extends InfoSource {
  UUID getDatasetId();
  UUID getExperimentId();
}
