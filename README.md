## **Getting Started**

Follow these steps to get the project up and running:

1. Clone the repository:

   ```bash
   git clone https://github.com/alibehrozi/wave.git
   ```

2. Setup keystore:

    - Copy your `release.keystore` file into the `app/config` directory.
    - Fill out the following properties in the `gradle.properties` file to access your keystore:

   > [**gradle.properties**](gradle.properties):
   > ```
   > RELEASE_KEY_PASSWORD=YOUR_ANDROID_KEY
   > RELEASE_KEY_ALIAS=YOUR_KEY_ALIAS
   > RELEASE_STORE_PASSWORD=YOUR_PASSWORD
   > ```

3. Build the project:

    - Open the project in Android Studio
    - You are now ready to compile the project.