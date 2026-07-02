package tom.jerry.vocab.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/wallpapers")
@CrossOrigin(origins = "*")
public class WallpaperController {

    // Define the absolute path to the wallpapers directory in the project root
    private final String WALLPAPER_DIR = System.getProperty("user.dir") + "/wallpapers";
    private final Random random = new Random();

    @GetMapping("/random")
    public ResponseEntity<Resource> getRandomWallpaper() {
        try {
            Path dirPath = Paths.get(WALLPAPER_DIR);
            
            // Create the directory if it doesn't exist
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }

            // List all files in the directory
            List<Path> imageFiles;
            try (Stream<Path> stream = Files.list(dirPath)) {
                imageFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.toString().toLowerCase();
                            return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                                   name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif");
                        })
                        .collect(Collectors.toList());
            }

            if (imageFiles.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }

            // Pick a random image
            Path randomImage = imageFiles.get(random.nextInt(imageFiles.size()));
            Resource resource = new UrlResource(randomImage.toUri());

            if (resource.exists() && resource.isReadable()) {
                // Determine content type
                String contentType = Files.probeContentType(randomImage);
                if (contentType == null) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
