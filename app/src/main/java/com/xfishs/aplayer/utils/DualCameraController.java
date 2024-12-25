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
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.MultiResolutionImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
    private MultiResolutionImageReader multiResolutionImageReader;
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

    public void openCamera(){
        HandlerThread thread = new HandlerThread("DualCamera");
        thread.start();
        handler = new Handler(thread.getLooper());
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CODE);
                return;
            }
            manager.openCamera(dualCamera.getLogicCameraId(), AsyncTask.SERIAL_EXECUTOR, cameraOpenCallBack);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback cameraOpenCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "相机已打开");
            try {
                config(cameraDevice); // 配置摄像头
                Log.d(TAG, "摄像头配置成功");
            } catch (Exception e) {
                Log.e(TAG, "摄像头配置失败", e);

            }
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
            OutputConfiguration outputConfiguration = new OutputConfiguration(surface1);
            outputConfiguration.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
            configurations.add(outputConfiguration);
            mPreViewBuilder.addTarget(surface1);

            SurfaceTexture texture2 = textureView2.getSurfaceTexture();
            Surface surface2 = new Surface(texture2);
            OutputConfiguration outputConfiguration2 = new OutputConfiguration(surface2);
            outputConfiguration2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());
            configurations.add(outputConfiguration2);
            mPreViewBuilder.addTarget(surface2);




            List<MultiResolutionStreamInfo> streams = new ArrayList<>();
            MultiResolutionStreamInfo streamInfo1 = new MultiResolutionStreamInfo(1920, 1080, dualCamera.getPhysicsCameraId1()); // 1920x1080，30fps
            MultiResolutionStreamInfo streamInfo2 = new MultiResolutionStreamInfo(1920, 1080, dualCamera.getPhysicsCameraId2());  // 1280x720，30fps
            streams.add(streamInfo1);
            streams.add(streamInfo2);
            multiResolutionImageReader = new MultiResolutionImageReader(
                    streams,
                    ImageFormat.JPEG,  // 假设您使用的是 JPEG 格式
                    1  // 最大图像数（例如：5个图像）
            );
            Collection<OutputConfiguration> outputConfig_sample = OutputConfiguration.createInstancesForMultiResolutionOutput(multiResolutionImageReader);
            for (OutputConfiguration config : outputConfig_sample) {
                configurations.add(config);

            }

            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIds = manager.getCameraIdList();
            for(String cameraId:cameraIds){

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                String levelString;
                switch (hardwareLevel) {
                    case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                        levelString = "LEGACY (仅支持 Camera1 API 功能)";
                        break;
                    case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                        levelString = "LIMITED (部分支持)";
                        break;
                    case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                        levelString = "FULL (完全支持)";
                        break;
                    case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                        levelString = "LEVEL_3 (扩展支持)";
                        break;
                    default:
                        levelString = "UNKNOWN";
                        break;
                }
                Log.d("Camera2Support", "Camera ID: " + cameraId + ", Support Level: " + levelString);

            }


//            Collection<OutputConfiguration> outputConfig_sample = OutputConfiguration.createInstancesForMultiResolutionOutput(multiResolutionImageReader);
//
//            for (OutputConfiguration config : outputConfig_sample) {
//                configurations.add(new OutputConfiguration(config.getSurface()));
//            }


//            imageReader1 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);
//            imageReader2 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);

//            mPreViewBuilder.addTarget(imageReader1.getSurface());
//            mPreViewBuilder.addTarget(imageReader2.getSurface());

//            OutputConfiguration outputConfiguration_im1 = new OutputConfiguration(imageReader1.getSurface());
//            outputConfiguration_im1.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
//            configurations.add(outputConfiguration_im1);
//
//            OutputConfiguration outputConfiguration_im2  = new OutputConfiguration(imageReader2.getSurface());
//            outputConfiguration_im2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());
//            configurations.add(outputConfiguration_im2);

//            mPreViewBuilder.addTarget(imageReader1.getSurface());
//            mPreViewBuilder.addTarget(imageReader2.getSurface());

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
        cameraDevice = cameraSession.getDevice();
        if (cameraDevice == null || cameraSession == null) {
            Log.e(TAG, "摄像头未初始化或会话为空");
            return;
        }

        try {
            CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            Collection<OutputConfiguration> outputConfig_sample = OutputConfiguration.createInstancesForMultiResolutionOutput(multiResolutionImageReader);
            for (OutputConfiguration config : outputConfig_sample) {
                captureRequest.addTarget(config.getSurface());
            }

            HandlerThread saveImageThread = new HandlerThread("SaveImageThread");
            saveImageThread.start();
            Handler saveImageHandler = new Handler(saveImageThread.getLooper());
            Executor saveImageExecutor = command -> saveImageHandler.post(command);
            multiResolutionImageReader.setOnImageAvailableListener(
                    new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            saveImageExecutor.execute(() -> {
                                Image image = reader.acquireLatestImage();
                                if (image != null) {
                                    try {
                                        saveImageToFile(image);  // 保存图像
                                        Log.d(TAG, "Image saved successfully.");
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error while saving image: " + e.getMessage());
                                    } finally {
                                        image.close();  // 确保关闭图像
                                    }
                                } else {
                                    Log.d(TAG, "Image is null.");
                                }
                            });
                        }
                    },
                    saveImageExecutor // 使用独立线程处理保存图像
            );





//            cameraSession.capture(captureRequest.build(), null,handler);
            HandlerThread backgroundThread = new HandlerThread("CameraBackgroundThread");
            backgroundThread.start();
            Handler mBackgroundHandler = new Handler(backgroundThread.getLooper());

            try {
                cameraSession.capture(captureRequest.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        Log.d(TAG, "------Capture completed.");
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Log.e(TAG, "------Capture failed with reason: " + failure.getReason());
                        if (failure.wasImageCaptured()) {
                            Log.e(TAG, "------Image was partially captured.");
                        } else {
                            Log.e(TAG, "------No image was captured.");
                        }
                    }
                }, mBackgroundHandler);  // 使用后台线程处理捕获过程
            }catch(Exception e) {
                Log.e(TAG, "Error while saving image: " + e.getMessage());
            }

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
    private void saveImageToFile(Image image) {
        // 获取图像的平面
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        // 将字节数组转换为 JPEG 格式，并将其保存到手机相册
        try {
            // 使用 ByteArrayOutputStream 将图像字节流保存为 JPEG 格式
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(bytes);
            byte[] jpegBytes = byteArrayOutputStream.toByteArray();

            // 获取 ContentResolver
            ContentResolver contentResolver = context.getContentResolver();

            // 设置要插入到 MediaStore 的数据
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "captured_image.jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyApp/"); // 图片保存的目录，可以自定义
            values.put(MediaStore.Images.Media.IS_PENDING, 1);  // 标记文件正在保存

            // 将图像插入到 MediaStore 中
            Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            // 如果插入成功
            if (imageUri != null) {
                // 将图像数据写入到 MediaStore
                try (OutputStream outputStream = contentResolver.openOutputStream(Objects.requireNonNull(imageUri))) {
                    outputStream.write(jpegBytes);
                }

                // 更新 MediaStore 的记录，标记图片已保存完毕
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                contentResolver.update(imageUri, values, null, null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭 Image
            image.close();
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

            if (multiResolutionImageReader != null) {
                multiResolutionImageReader.close();
                multiResolutionImageReader = null;
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