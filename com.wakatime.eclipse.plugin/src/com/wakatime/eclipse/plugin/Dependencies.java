/* ==========================================================
File:        Dependencies.java
Description: Manages plugin dependencies.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


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

    private static String pythonLocation = null;

    public Dependencies() {
    }

    public boolean isPythonInstalled() {
        return Dependencies.getPythonLocation() != null;
    }

    public static String getPythonLocation() {
        if (Dependencies.pythonLocation != null)
            return Dependencies.pythonLocation;
        String []paths = new String[] {
                "pythonw",
                "python",
                "/usr/local/bin/python",
                "/usr/bin/python",
                "\\python37\\pythonw",
                "\\Python37\\pythonw",
                "\\python36\\pythonw",
                "\\Python36\\pythonw",
                "\\python35\\pythonw",
                "\\Python35\\pythonw",
                "\\python34\\pythonw",
                "\\Python34\\pythonw",
                "\\python33\\pythonw",
                "\\Python33\\pythonw",
                "\\python32\\pythonw",
                "\\Python32\\pythonw",
                "\\python31\\pythonw",
                "\\Python31\\pythonw",
                "\\python30\\pythonw",
                "\\Python30\\pythonw",
                "\\python27\\pythonw",
                "\\Python27\\pythonw",
                "\\python26\\pythonw",
                "\\Python26\\pythonw",
                "\\python37\\python",
                "\\Python37\\python",
                "\\python36\\python",
                "\\Python36\\python",
                "\\python35\\python",
                "\\Python35\\python",
                "\\python34\\python",
                "\\Python34\\python",
                "\\python33\\python",
                "\\Python33\\python",
                "\\python32\\python",
                "\\Python32\\python",
                "\\python31\\python",
                "\\Python31\\python",
                "\\python30\\python",
                "\\Python30\\python",
                "\\python27\\python",
                "\\Python27\\python",
                "\\python26\\python",
                "\\Python26\\python",
        };
        for (int i=0; i<paths.length; i++) {
            try {
                String[] cmds = { paths[i], "--version" };
                Runtime.getRuntime().exec(cmds);
                Dependencies.pythonLocation = paths[i];
                break;
            } catch (Exception e) { }
        }
        return Dependencies.pythonLocation;
    }
    
    public void installPython() {
        if (System.getProperty("os.name").contains("Windows")) {
            String pyVer = "3.5.0";
            String arch = "win32";
            if (is64bit()) arch = "amd64";
            String url = "https://www.python.org/ftp/python/" + pyVer + "/python-" + pyVer + "-embed-" + arch + ".zip";

        	File cli = new File(WakaTime.getWakaTimeCLI());
        	File dir = cli.getParentFile().getParentFile();
            String outFile = combinePaths(dir.getAbsolutePath(), "python.zip");
            if (downloadFile(url, outFile)) {

                File targetDir = new File(combinePaths(dir.getAbsolutePath(), "python"));

                // extract python
                try {
                    unzip(outFile, targetDir);
                } catch (IOException e) {
                    WakaTime.error(e.toString());
                }
                File zipFile = new File(outFile);
                zipFile.delete();
            }
        }
    }

    public boolean isCLIInstalled() {
        File cli = new File(WakaTime.getWakaTimeCLI());
        return cli.exists();
    }

    public void installCLI() {
        File cli = new File(WakaTime.getWakaTimeCLI());

        String url = "https://codeload.github.com/wakatime/wakatime/zip/master";
        String zipFile = combinePaths(cli.getParentFile().getParentFile().getParentFile().getAbsolutePath(), "wakatime.zip");
        File outputDir = cli.getParentFile().getParentFile().getParentFile();

        // Delete old wakatime-master directory if it exists
        File dir = cli.getParentFile().getParentFile();
        if (dir.exists()) {
            deleteDirectory(dir);
        }

        // download wakatime-master.zip file
        if (downloadFile(url, zipFile)) {

            // unzip wakatime.zip file
            try {
                WakaTime.log("Extracting wakatime.zip ...");
                this.unzip(zipFile, outputDir);
                File oldZipFile = new File(zipFile);
                oldZipFile.delete();
                WakaTime.log("Finished installing WakaTime dependencies.");
            } catch (FileNotFoundException e) {
                WakaTime.error("Error", e);
            } catch (IOException e) {
                WakaTime.error("Error", e);
            }
        }
    }

    public boolean downloadFile(String url, String saveAs) {
        File outFile = new File(saveAs);

        // create output directory if does not exist
        File outDir = outFile.getParentFile();
        if (!outDir.exists())
            outDir.mkdirs();

        WakaTime.log("Downloading " + url + " to " + outFile.toString());

        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(url);
        try {

            // download file
            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();

            // save file contents
            DataOutputStream os = new DataOutputStream(new FileOutputStream(outFile));
            entity.writeTo(os);
            os.close();

            return true;

        } catch (ClientProtocolException e) {
            WakaTime.error("Error", e);
        } catch (FileNotFoundException e) {
            WakaTime.error("Error", e);
        } catch (IOException e) {
            WakaTime.error("Error", e);
        }

        return false;
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
                // WakaTime.log("Creating directory: "+newFile.getParentFile().getAbsolutePath());
                newFile.mkdirs();
            } else {
                // WakaTime.log("Extracting File: "+newFile.getAbsolutePath());
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

    private static void deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        path.delete();
    }

    public static String combinePaths(String... args) {
        File path = null;
        for (String arg : args) {
            if (path == null)
                path = new File(arg);
            else
                path = new File(path, arg);
        }
        if (path == null)
            return null;
        return path.toString();
    }
    
    public static boolean is64bit() {
        boolean is64bit = false;
        if (System.getProperty("os.name").contains("Windows")) {
            is64bit = (System.getenv("ProgramFiles(x86)") != null);
        } else {
            is64bit = (System.getProperty("os.arch").indexOf("64") != -1);
        }
        return is64bit;
    }
}
