package app;

import controllers.MainController;

public class Main {
    public static void main(String[] args) {
        // Jalankan aplikasi melalui Controller
        MainController app = new MainController();
        app.startApp();
    }
}