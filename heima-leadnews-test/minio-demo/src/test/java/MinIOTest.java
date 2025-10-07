import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.FileInputStream;

public class MinIOTest {
    public static void main(String[] args) {
        // 创建MinIO客户端实例，配置连接凭证和服务器端点
        FileInputStream fileInputStream = null;
        try {
            fileInputStream=new FileInputStream("C:\\Java\\WorkSpace\\heima-leadnews\\list.html");
            MinioClient minioClient = MinioClient.builder().credentials("minio", "minio123")
                    .endpoint("http://172.18.23.195:9000").build();

            // 构建文件上传参数对象，指定文件名、内容类型、存储桶名称和文件流
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .object("list.html")//文件在存储桶中的路径和名称，这里指定为"list.html"
                    .contentType("text/html")//文件的内容类型，这里指定为"text/html"表示HTML文档
                    .bucket("leadnews")//存储桶名称，这里指定为"leadnews"
                    .stream(fileInputStream, fileInputStream.available(), -1)//文件输入流，通过fileInputStream提供文件数据，fileInputStream.available()获取文件大小，-1表示不指定分片大小
                    .build();

            // 执行文件上传操作，将文件保存到MinIO存储服务
            minioClient.putObject(putObjectArgs);

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
