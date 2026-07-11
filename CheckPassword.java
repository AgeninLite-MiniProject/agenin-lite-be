import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CheckPassword {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$12$VLEf7V2o4Z.m1NNGBjO3R.Msgs2fBbft/L625DdD1mbCKqK8leQY.";
        String[] passwords = {"admin", "password", "123456", "agenin", "ageninlite", "admin123", "ageninlite1", "ageninlite2"};
        for (String pwd : passwords) {
            if (encoder.matches(pwd, hash)) {
                System.out.println("Password is: " + pwd);
                return;
            }
        }
        System.out.println("Not found");
    }
}
