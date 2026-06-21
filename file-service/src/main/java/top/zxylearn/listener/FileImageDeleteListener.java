package top.zxylearn.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.service.FileService;

@Component
public class FileImageDeleteListener {

    private static final Logger log = LoggerFactory.getLogger(FileImageDeleteListener.class);

    private final FileService fileService;

    public FileImageDeleteListener(FileService fileService) {
        this.fileService = fileService;
    }

    @RabbitListener(queues = MqConstants.FILE_IMAGE_DELETE_QUEUE)
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        try {
            fileService.deleteImageByUrl(imageUrl);
            log.info("已删除旧图片 imageUrl={}", imageUrl);
        } catch (IllegalArgumentException ex) {
            log.info("旧图片不属于可删除的OSS图片，跳过 imageUrl={}, reason={}", imageUrl, ex.getMessage());
        }
    }
}
