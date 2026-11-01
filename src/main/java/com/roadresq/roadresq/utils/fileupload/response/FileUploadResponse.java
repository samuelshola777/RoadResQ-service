package com.roadresq.roadresq.utils.fileupload.response;


import com.roadresq.roadresq.utils.fileupload.FileUploadCategory;
import com.roadresq.roadresq.utils.fileupload.FileUsageType;

public record FileUploadResponse(
        String fileUrl,
        String publicId,
        String originalFileName,
        String format,
        String resourceType,
        Long fileSizeBytes,
        FileUploadCategory category,
        FileUsageType usageType
) {
}