package com.roadresq.roadresq.utils.fileupload;



import com.roadresq.roadresq.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "managed_files")
public class ManagedFile extends BaseEntity {

    private String originalFileName;

    @Indexed(unique = true)
    private String fileUrl;

    private String secureUrl;

    @Indexed(unique = true)
    private String publicId;

    private String format;

    private String resourceType;

    private Long fileSizeBytes;

    private FileUploadCategory category;

    private FileUsageType usageType;
}