package com.coldguard.simulator.application;

public interface SimulatorMetrics {

  void sent(int count);

  void dropped(int count);

  void rejected(int count);
}
