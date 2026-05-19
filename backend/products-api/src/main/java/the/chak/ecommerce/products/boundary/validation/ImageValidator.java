package the.chak.ecommerce.products.boundary.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ImageValidator implements ConstraintValidator<ValidImage, byte[]> {

    @Override
    public boolean isValid(byte[] image, ConstraintValidatorContext context) {
        if (image == null || image.length == 0) {
            return true;
        }
        // Check for JPEG (FF D8 FF)
        if (image.length >= 3 && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8
                && (image[2] & 0xFF) == 0xFF) {
            return true;
        }
        // Check for PNG (89 50 4E 47 0D 0A 1A 0A)
        if (image.length >= 8 && (image[0] & 0xFF) == 0x89 && (image[1] & 0xFF) == 0x50
                && (image[2] & 0xFF) == 0x4E && (image[3] & 0xFF) == 0x47
                && (image[4] & 0xFF) == 0x0D && (image[5] & 0xFF) == 0x0A
                && (image[6] & 0xFF) == 0x1A && (image[7] & 0xFF) == 0x0A) {
            return true;
        }
        // Check for GIF (GIF87a or GIF89a)
        if (image.length >= 6 && (image[0] & 0xFF) == 'G' && (image[1] & 0xFF) == 'I'
                && (image[2] & 0xFF) == 'F' && (image[3] & 0xFF) == '8'
                && ((image[4] & 0xFF) == '7' || (image[4] & 0xFF) == '9')
                && (image[5] & 0xFF) == 'a') {
            return true;
        }
        // Check for WebP (RIFF....WEBP)
        if (image.length >= 12 && (image[0] & 0xFF) == 'R' && (image[1] & 0xFF) == 'I'
                && (image[2] & 0xFF) == 'F' && (image[3] & 0xFF) == 'F' && (image[8] & 0xFF) == 'W'
                && (image[9] & 0xFF) == 'E' && (image[10] & 0xFF) == 'B'
                && (image[11] & 0xFF) == 'P') {
            return true;
        }

        return false;
    }
}
