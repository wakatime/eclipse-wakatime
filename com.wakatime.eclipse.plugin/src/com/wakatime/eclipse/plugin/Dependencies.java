package com.wakatime.eclipse.plugin;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class Dependencies {
    /**
     * The constructor.
     */
    public Dependencies() {
    }

    public boolean isPythonInstalled() {
        try {
            Runtime.getRuntime().exec("python");
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public boolean areDependenciesInstalled() {
        File cli = new File(Activator.getWakaTimeCLI());
        return (cli.exists() && !cli.isDirectory());
    }

    public void installDependencies() {
        File cli = new File(Activator.getWakaTimeCLI());
        if (!cli.getParentFile().getParentFile().exists())
            cli.getParentFile().getParentFile().mkdirs();

        String url = "https://codeload.github.com/wakatime/wakatime/zip/master";
        String zipFile = cli.getParentFile().getParentFile().getAbsolutePath()+File.separator+"wakatime.zip";
        File outputDir = cli.getParentFile().getParentFile();

        // download wakatime-master.zip file
        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(url);
        HttpResponse response = null;
        try {
            response = httpclient.execute(httpget);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        HttpEntity entity = response.getEntity();
        try {
            System.out.println("Downloading wakatime-cli to wakatime.zip ...");
            DataOutputStream os = new DataOutputStream(new FileOutputStream(zipFile));
            entity.writeTo(os);
            os.close();
            System.out.println("Extracting wakatime.zip ...");
            this.unzip(zipFile, outputDir);
            File oldZipFile = new File(zipFile);
            oldZipFile.delete();
            System.out.println("Finished installing WakaTime dependencies.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void unzip(String zipFile, File outputDir) throws IOException {
        if(!outputDir.exists())
            outputDir.mkdirs();

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry ze = zis.getNextEntry();

        while (ze != null) {
            String fileName = ze.getName();
            File newFile = new File(outputDir, fileName);

            if (ze.isDirectory()) {
                // System.out.println("Creating directory: "+newFile.getParentFile().getAbsolutePath());
                newFile.mkdirs();
            } else {
                // System.out.println("Extracting File: "+newFile.getAbsolutePath());
                FileOutputStream fos = new FileOutputStream(newFile.getAbsolutePath());
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }

            ze = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }
}
