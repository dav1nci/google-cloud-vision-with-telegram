package com.lagrange;/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// [BEGIN import_libraries]
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionScopes;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.common.collect.ImmutableList;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
// [END import_libraries]

/**
 * A sample application that uses the Vision API to label an image.
 */
@SuppressWarnings("serial")
public class LabelApp {
    /**
     * Be sure to specify the name of your application. If the application name is {@code null} or
     * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
     */
    private static final String APPLICATION_NAME = "Google-VisionLabelSample/1.0";

    private static final int MAX_LABELS = 3;

    private static Path pathToImage;

    // [START run_application]
    /**
     * Annotates an image using the Vision API.
     */
    public static void main(String[] args) throws IOException, GeneralSecurityException {

        if (args.length != 1) {
            System.err.println("Missing imagePath argument.");
            System.err.println("Usage:");
            System.err.printf("\tjava %s imagePath\n", LabelApp.class.getCanonicalName());
            System.exit(1);
        }
        pathToImage = Paths.get(args[0]);

        LabelApp app = new LabelApp(getVisionService());
        printLabels(System.out, pathToImage, app.labelImage(pathToImage, MAX_LABELS));
    }

    /**
     * Try to send message with labels and its probabilities to telegram
     */
    private static void sendMessageToTelegramBot(List<EntityAnnotation> labels) throws IOException {
        String urlSendMessage = "https://api.telegram.org/<botID>/sendMessage";

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(urlSendMessage);
        List <NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("text", buildMessage(labels)));
        nvps.add(new BasicNameValuePair("chat_id", "<chatID>"));
        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        CloseableHttpResponse response = httpclient.execute(httpPost);

        try {
            System.out.println(response.getStatusLine());
        } finally {
            response.close();
        }
        System.out.println("Done sending text");
    }

    private static void sendPhotoToTelegramBot()
    {
        File file = new File(pathToImage.toUri());
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("photo", file, ContentType.create("image/jpeg"), file.getName())
                .addTextBody("chat_id", "<chatID>")
                .build();

        String urlSendPhoto = "https://api.telegram.org/<botID>/sendPhoto";

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(urlSendPhoto);
        httpPost.setEntity(httpEntity);
        try {
            CloseableHttpResponse response = httpclient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Done sending photo");
    }

    /**
     * Build text message based on Google Cloud Vision result
     */
    private static String buildMessage(List<EntityAnnotation> labels)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (EntityAnnotation label : labels) {
            stringBuilder.append(label.getDescription())
                    .append(" ").append(label.getScore())
                    .append("\n");
        }
        return stringBuilder.toString();
    }

    /**
     * Prints the labels received from the Vision API.
     */
    public static void printLabels(PrintStream out, Path imagePath, List<EntityAnnotation> labels) {
        out.printf("Labels for image %s:\n", imagePath);
        for (EntityAnnotation label : labels) {
            out.printf(
                "\t%s (score: %.3f)\n",
                label.getDescription(),
                label.getScore());
        }
        try {
            sendPhotoToTelegramBot();
            sendMessageToTelegramBot(labels);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (labels.isEmpty()) {
            out.println("\tNo labels found.");
        }

    }
    // [END run_application]

    // [START authenticate]
    /**
     * Connects to the Vision API using Application Default Credentials.
     */
    public static Vision getVisionService() throws IOException, GeneralSecurityException {
        GoogleCredential credential =
                GoogleCredential.getApplicationDefault().createScoped(VisionScopes.all());
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        return new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
    // [END authenticate]

    private final Vision vision;

    /**
     * Constructs a {@link LabelApp} which connects to the Vision API.
     */
    public LabelApp(Vision vision) {
        this.vision = vision;
    }

    /**
     * Gets up to {@code maxResults} labels for an image stored at {@code path}.
     */
    public List<EntityAnnotation> labelImage(Path path, int maxResults) throws IOException {
        // [START construct_request]
        byte[] data = Files.readAllBytes(path);

        AnnotateImageRequest request =
                new AnnotateImageRequest()
                        .setImage(new Image().encodeContent(data))
                        .setFeatures(ImmutableList.of(
                                new Feature()
                                        .setType("LABEL_DETECTION")
                                        .setMaxResults(maxResults)));
        Vision.Images.Annotate annotate =
                vision.images()
                        .annotate(new BatchAnnotateImagesRequest().setRequests(ImmutableList.of(request)));
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotate.setDisableGZipContent(true);
        // [END construct_request]

        // [START parse_response]
        BatchAnnotateImagesResponse batchResponse = annotate.execute();
        assert batchResponse.getResponses().size() == 1;
        AnnotateImageResponse response = batchResponse.getResponses().get(0);
        if (response.getLabelAnnotations() == null) {
            throw new IOException(
                    response.getError() != null
                            ? response.getError().getMessage()
                            : "Unknown error getting image annotations");
        }
        return response.getLabelAnnotations();
        // [END parse_response]
    }
}
