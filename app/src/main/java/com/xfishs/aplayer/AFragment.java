package com.xfishs.aplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.xfishs.aplayer.utils.DualCamera;
import com.xfishs.aplayer.utils.DualCameraController;
import com.xfishs.aplayer.utils.DualCameraFactoryUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AFragment extends Fragment {
    private static final String TAG = "AFragment";

    //定义一个常量，用于区分当前的权限请求
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    //双镜控制类
    private DualCameraController dualCameraController;
    //页面显示类，保存两个 TextureView
    private List<TextureView> mTextureViews;
    private Button btnCapture;

    private boolean isRecording = false;

    private Button btnRecord;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_main, container, false);
        //初始化组件
        findView(v);
        // 初始化拍照按钮
        btnCapture = v.findViewById(R.id.btn_capture);
        btnCapture.setOnClickListener(v1 -> {
            if (dualCameraController != null) {
                dualCameraController.captureImages(); // 调用控制器的拍照方法
            }
        });

        btnRecord = v.findViewById(R.id.btn_record);
        btnRecord.setOnClickListener(v1 -> {
            if (dualCameraController != null) {
                if (isRecording) {
                    // 停止录制
                    stopRecording();
                } else {
                    // 开始录制
                    startRecording();
                }
            }
        });

        //权限检查
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openDualCamera();
        } else {
            //否则去请求相机权限
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CODE);
        }
        return v;
    }

    private void startRecording() {
        if (dualCameraController != null) {
            dualCameraController.startDualCameraRecording(); // 启动双摄像头录制
            isRecording = true;
            btnRecord.setText("停止录制"); // 修改按钮文本为停止
        }
    }

    private void stopRecording() {
        if (dualCameraController != null) {
            dualCameraController.stopDualCameraRecording(); // 停止双摄像头录制
            btnRecord.setText("开始录制"); // 修改按钮文本为开始
        }
    }


            //打开双镜
    private void openDualCamera() {
        //获取双镜类
        DualCamera dualCamera = DualCameraFactoryUtil.getDualCamera(getActivity());
        //打开双镜
        dualCameraController = new DualCameraController(getActivity(), mTextureViews.get(0), mTextureViews.get(1), dualCamera);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //销毁组件
        dualCameraController.onDestroyView();
    }


    //初始化组件
    private void findView(View view) {
        mTextureViews = new ArrayList<>();
        List<Integer> ids = Arrays.asList(R.id.tv_textview0, R.id.tv_textview1);
        ids.forEach(v -> mTextureViews.add(view.findViewById(v)));
    }

    //权限检查回执


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                if (permissions[0].equals(Manifest.permission.CAMERA)) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        //同意了再去打开相机
                        openDualCamera();
                    }
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + requestCode);
        }
    }
}
