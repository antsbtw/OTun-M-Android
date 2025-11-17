package com.situstechnologies.OXray.aidl;

import com.situstechnologies.OXray.aidl.IServiceCallback;

interface IService {
  int getStatus();
  void registerCallback(in IServiceCallback callback);
  oneway void unregisterCallback(in IServiceCallback callback);
}