# Mifare-Ultralight-Updated
An attempt to update the Mifare++ Ultralight apk to support android 14. <br>
Credit to emutag for the app

# Current Status
Build is working ✅<br>
Android is willing to install ✅<br>
App Starts up ✅<br>
Unable to read card ❌

# Speculations
Method used to communicate with Android NFC is too old. May need a newer library like nfcjlib.

# Current Error Log
`logcat *:E` <br>
output:
```
07-08 01:12:56.479  6236  6236 E libnfc_nci: [ERROR:NativeNfcTag.cpp(604)] nativeNfcTag_doConnect:  doConnect sCurrentConnectedTargetProtocol 2 sCurrentConnectedTargetType 1
07-08 01:12:56.505  6236  6236 E libnfc_nci: [ERROR:NativeNfcTag.cpp(604)] nativeNfcTag_doConnect:  doConnect sCurrentConnectedTargetProtocol 2 sCurrentConnectedTargetType 9
07-08 01:12:56.533  6236  6236 E CtaNfcBehavior: Test Mode is off!
07-08 01:12:56.542  6172  6306 E VqeAppWatchList: No app audio config available, check your config XML!
07-08 01:12:56.546  1667  3691 E AHAL: AudioStream: out_update_source_metadata_v7: 1121: track count is 1
07-08 01:12:56.546  1642  7271 E Parcel  : Attempt to read from protected data in Parcel 0x6b620f5a00
07-08 01:12:56.546  1642  7271 E Parcel  : Attempt to read from protected data in Parcel 0x6b620f5a00
07-08 01:12:56.546  1642  7271 E Parcel  : Attempt to read from protected data in Parcel 0x6b620f5a00
07-08 01:12:56.546  1642  7271 E Parcel  : Expecting header 0x53595354 but found 0x0. Mixing copies of libbinder?
07-08 01:12:56.558  1714  1714 E android.hardware.vibrator-cs40l25: Failed to read device/asp_enable (0): Success
07-08 01:12:56.565  1667 16697 E ACDB    : AcdbCmdGetGraphAlias:3870 Error[19]: Graph Alias chunk data does not exist. Is it enabled?.
07-08 01:12:56.565  1667 16697 E gsl     : gsl_get_graph_alias:2241 acdb get graph alias failed 19, len 255
07-08 01:12:56.604  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.605  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.605  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.605  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.608  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.608  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.608  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.608  1667 16697 E ACDB    : AcdbCmdGetProcSubgraphCalDataPersist:8217 Error[19]: No calibration found
07-08 01:12:56.612  1667 16697 E AGM: graph: configure_buffer_params: 296 Unsupported buffer mode : 0, Default to Blocking
07-08 01:12:57.594  1642  7475 E Parcel  : Attempt to read from protected data in Parcel 0x6b586f8a00
07-08 01:12:57.594  1642  7475 E Parcel  : Attempt to read from protected data in Parcel 0x6b586f8a00
07-08 01:12:57.594  1642  7475 E Parcel  : Attempt to read from protected data in Parcel 0x6b586f8a00
07-08 01:12:57.594  1642  7475 E Parcel  : Expecting header 0x53595354 but found 0x0. Mixing copies of libbinder?
```
