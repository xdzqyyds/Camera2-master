package com.xfishs.aplayer.utils;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import java.util.Arrays;
import java.util.Set;

public class DualCameraFactoryUtil {

    private final static String TAG = "DualCameraFactoryUtil";


    /**
     * 获取双镜类
     * @param context
     * @return
     */
    public static DualCamera getDualCamera(Context context){
        DualCamera dualCamera = new DualCamera();
        //获取管理类
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        assert manager != null;
        try {
            //获取所有逻辑ID
            String[] cameraIdList = manager.getCameraIdList();

            //获取逻辑摄像头下拥有多个物理摄像头的类 作为双镜类
            for (String id : cameraIdList) {
                try {
                    CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(id);
                    Set<String> physicalCameraIds = cameraCharacteristics.getPhysicalCameraIds();
                    Log.d(TAG, "逻辑ID：" + id + " 下的物理ID: " + Arrays.toString(physicalCameraIds.toArray()));
                    if (physicalCameraIds.size() >= 2) {
                        dualCamera.setLogicCameraId(id);
                        Object[] objects = physicalCameraIds.toArray();
                        //获取前两个物理摄像头作为双镜头
                        dualCamera.setPhysicsCameraId1(String.valueOf(objects[0]));
                        dualCamera.setPhysicsCameraId2(String.valueOf(objects[1]));
                        return dualCamera;
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }



}
