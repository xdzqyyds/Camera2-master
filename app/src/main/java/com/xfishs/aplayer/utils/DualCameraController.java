package com.xfishs.aplayer.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.os.Build;
import android.hardware.camera2.CameraCharacteristics;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.hardware.camera2.CameraManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public class DualCameraController {
    private static final String TAG = "DualCameraController";

    private static final Integer PERMISSIONS_REQUEST_CODE = 1000;

    // UI 组件
    private TextureView textureView1;
    private TextureView textureView2;

    // Camera 相关变量
    private CameraDevice cameraDevice; // 摄像头实例
    private CameraCaptureSession cameraSession; // 捕获会话
    private CaptureRequest.Builder mPreViewBuilder; // 预览请求构造器
    private ImageReader imageReader1; // 第一个摄像头的 ImageReader
    private ImageReader imageReader2; // 第二个摄像头的 ImageReader

    // 线程和上下文
    private Activity context;
    private Handler handler; // 用于后台线程的 Handler
    private DualCamera dualCamera; // 双摄像头对象

    private boolean isCameraOpening = false;

    public DualCameraController(Activity context, TextureView textureView1, TextureView textureView2, DualCamera dualCamera) {
        this.context = context;
        this.textureView1 = textureView1;
        this.textureView2 = textureView2;
        this.dualCamera = dualCamera;

        // 设置监听器
        this.textureView2.setSurfaceTextureListener(surfaceTextureListener);
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(); // SurfaceTexture 准备好后打开摄像头
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    public void openCamera() {
        if (isCameraOpening) {
            Log.e(TAG, "相机正在初始化，跳过此次调用");
            return;
        }
        isCameraOpening = true;

        try {
            HandlerThread thread = new HandlerThread("DualCameraThread");
            thread.start();
            handler = new Handler(thread.getLooper());

            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CODE);
                return;
            }
            manager.openCamera(dualCamera.getLogicCameraId(), cameraOpenCallBack, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "打开相机失败", e);
        } finally {
            isCameraOpening = false;
        }
    }

    private CameraDevice.StateCallback cameraOpenCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            Log.d(TAG, "相机已打开");
            cameraDevice = device; // 保存 CameraDevice 实例
            config(cameraDevice); // 配置摄像头
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "相机连接断开");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, "相机打开失败，错误代码：" + error);
            releaseResources(); // 释放资源

            if (error == CameraDevice.StateCallback.ERROR_CAMERA_DEVICE || error == CameraDevice.StateCallback.ERROR_CAMERA_SERVICE) {
                handler.postDelayed(() -> {
                    try {
                        openCamera();
                    } catch (Exception e) {
                        Log.e(TAG, "重新打开相机失败", e);
                    }
                }, 1000);
            }
        }
    };

    public void config(CameraDevice cameraDevice) {
        try {
            List<OutputConfiguration> configurations = new ArrayList<>();
            mPreViewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            SurfaceTexture texture1 = textureView1.getSurfaceTexture();
            Surface surface1 = new Surface(texture1);
            OutputConfiguration configuration =new OutputConfiguration(surface1);
            configuration.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
            configurations.add(configuration);
            mPreViewBuilder.addTarget(surface1);

            SurfaceTexture texture2 = textureView2.getSurfaceTexture();
            Surface surface2 = new Surface(texture2);
            OutputConfiguration configuration2 =new OutputConfiguration(surface2);
            configuration2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());
            configurations.add(configuration2);
            mPreViewBuilder.addTarget(surface2);

            imageReader1 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);
            imageReader2 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);
            OutputConfiguration configuration_im1 =new OutputConfiguration(imageReader1.getSurface());
            configuration_im1.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
            OutputConfiguration configuration_im2 =new OutputConfiguration(imageReader2.getSurface());
            configuration_im2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());


            configurations.add(configuration_im1);
            configurations.add(configuration_im2);

            Executor executor = command -> handler.post(command);

            SessionConfiguration sessionConfig = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    configurations,
                    executor,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                cameraSession = session;
                                cameraSession.setRepeatingRequest(mPreViewBuilder.build(), null, handler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "会话配置失败", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "配置会话失败");
                        }
                    });
            cameraDevice.createCaptureSession(sessionConfig);
        } catch (CameraAccessException e) {
            Log.e(TAG, "配置相机失败", e);
        }
    }

    public void captureImages() {
        if (cameraDevice == null || cameraSession == null) {
            Log.e(TAG, "摄像头未初始化或会话为空");
            return;
        }

        try {
            if (imageReader1 == null || imageReader2 == null) {
                Log.e(TAG, "ImageReader 未初始化");
                return;
            }

            //检查关于支持camrea2-API所有功能的支持情况
//            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
//            CameraCharacteristics characteristics = manager.getCameraCharacteristics(dualCamera.getPhysicsCameraId1());
//            int[] availableTemplates = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
//
//            if (Arrays.asList(availableTemplates).contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
//                // 检查是否支持 TEMPLATE_STILL_CAPTURE
//            } else {
//                Log.e(TAG, "Device does not support TEMPLATE_STILL_CAPTURE");
//            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                cameraSession.abortCaptures();
            }
            cameraSession.stopRepeating();

            CaptureRequest.Builder captureRequest1 = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest1.addTarget(imageReader1.getSurface());
            captureRequest1.set(CaptureRequest.JPEG_ORIENTATION, 90);

            CaptureRequest.Builder captureRequest2 = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest2.addTarget(imageReader2.getSurface());
            captureRequest2.set(CaptureRequest.JPEG_ORIENTATION, 90);

            imageReader1.setOnImageAvailableListener(reader -> saveImageToGalleryAsync(reader.acquireLatestImage()), handler);
            imageReader2.setOnImageAvailableListener(reader -> saveImageToGalleryAsync(reader.acquireLatestImage()), handler);



            try {
                List<CaptureRequest> burstRequests = Arrays.asList(captureRequest1.build(), captureRequest2.build());
                cameraSession.captureBurst(burstRequests, null, handler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "捕获失败", e);
                releaseResources();
            }

            resumePreview();

        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照失败", e);
            releaseResources();
        }
    }

    private void saveImageToGalleryAsync(Image image) {
        new Thread(() -> saveImageToGallery(image)).start();
    }

    private void saveImageToGallery(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/MyApp");
        values.put(MediaStore.Images.Media.IS_PENDING, true);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream fos = resolver.openOutputStream(uri)) {
                fos.write(bytes);
                fos.flush();
                Log.d(TAG, "图片已保存到: " + uri.toString());
            } catch (IOException e) {
                Log.e(TAG, "图片保存失败", e);
            } finally {
                values.put(MediaStore.Images.Media.IS_PENDING, false);
                resolver.update(uri, values, null, null);
                if (image != null) {
                    image.close();
                }
            }
        } else {
            Log.e(TAG, "无法获取 MediaStore 的 URI");
            if (image != null) {
                image.close();
            }
        }
    }

    private void resumePreview() {
        try {
            if (cameraSession != null && mPreViewBuilder != null) {
                cameraSession.setRepeatingRequest(mPreViewBuilder.build(), null, handler);
            } else {
                Log.e(TAG, "无法恢复预览：会话或请求为空");
                releaseResources();
                openCamera();
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "恢复预览失败", e);
            releaseResources();
            openCamera();
        }
    }

    private void releaseResources() {
        try {
            if (cameraSession != null) {
                try {
                    cameraSession.stopRepeating();
                    cameraSession.abortCaptures();
                } catch (CameraAccessException e) {
                    Log.e(TAG, "停止请求失败", e);
                }
                cameraSession.close();
                cameraSession = null;
            }

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }

            if (imageReader1 != null) {
                imageReader1.close();
                imageReader1 = null;
            }

            if (imageReader2 != null) {
                imageReader2.close();
                imageReader2 = null;
            }

            Log.d(TAG, "相机资源已成功释放");
        } catch (Exception e) {
            Log.e(TAG, "释放相机资源失败", e);
        }
    }

    public void onDestroyView() {
        releaseResources();
    }
}