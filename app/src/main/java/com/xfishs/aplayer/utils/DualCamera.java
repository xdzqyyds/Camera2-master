package com.xfishs.aplayer.utils;

public class DualCamera {

    //逻辑相机ID（厂商分配）
    private String logicCameraId;

    //相机物理id 1
    private String physicsCameraId1;

    //相机物理id 2
    private String physicsCameraId2;


    public String getLogicCameraId() {
        return logicCameraId;
    }

    public void setLogicCameraId(String logicCameraId) {
        this.logicCameraId = logicCameraId;
    }

    public String getPhysicsCameraId1() {
        return physicsCameraId1;
    }

    public void setPhysicsCameraId1(String physicsCameraId1) {
        this.physicsCameraId1 = physicsCameraId1;
    }

    public String getPhysicsCameraId2() {
        return physicsCameraId2;
    }

    public void setPhysicsCameraId2(String physicsCameraId2) {
        this.physicsCameraId2 = physicsCameraId2;
    }
}
