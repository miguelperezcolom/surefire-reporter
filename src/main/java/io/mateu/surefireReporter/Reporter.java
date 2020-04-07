package io.mateu.surefireReporter;

import okhttp3.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.File;
import java.io.IOException;

public class Reporter {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.out.println("usage: Reporter projectDir devopsEndpoint");
        } else {
            procesar(args[0], args[1]);
        }
    }

    private static void procesar(String projectPath, String devopsEndpoint) {

        File d = new File(projectPath);

        Element xml = new Element("root");
        try {
            procesar(xml, d);
        } catch (JDOMException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(new XMLOutputter(Format.getPrettyFormat()).outputString(xml));

        post(devopsEndpoint, xml);

    }

    private static void post(String devopsEndpoint, Element xml) {

        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(new XMLOutputter(Format.getPrettyFormat()).outputString(xml), JSON);
        Request request = new Request.Builder()
                .url(devopsEndpoint + "/" + xml.getChild("project").getAttributeValue("id"))
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            System.out.println(response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void procesar(Element xml, File f) throws JDOMException, IOException {
        if (f.isDirectory()) {
            if ("surefire-reports".equalsIgnoreCase(f.getName())) {
                for (File x : f.listFiles()) {
                    if (x.getName().toLowerCase().startsWith("test-") && x.getName().toLowerCase().endsWith(".xml")) {
                        procesar(xml, x);
                    }
                }
            } else {
                for (File x : f.listFiles()) {
                    if ("pom.xml".equalsIgnoreCase(x.getName())) {
                        procesar(xml, x);
                    }
                }
            }
        } else {
            if ("pom.xml".equalsIgnoreCase(f.getName())) {
                procesarPom(xml, f);
            } else if (f.getName().toLowerCase().startsWith("test-") && f.getName().toLowerCase().endsWith(".xml")) {
                procesarXml(xml, f);
            }
        }
    }

    private static void procesarXml(Element xml, File f) throws JDOMException, IOException {
        Document d = new SAXBuilder().build(f);
        Element dp = d.getRootElement();
        Element er;
        xml.addContent(er = new Element("suite"));
        er.addContent(dp.detach());
    }

    private static void procesarPom(Element xml, File f) throws JDOMException, IOException {
        if (f.exists()) {
            if ("root".equalsIgnoreCase(xml.getName())) {
                Document d = new SAXBuilder().build(f);
                clean(d);
                Element dp = d.getRootElement();

                String gid = dp.getChildText("groupId");
                String aid = dp.getChildText("artifactId");
                String v = dp.getChildText("version");

                Element ep;
                xml.addContent(ep = new Element("project"));
                ep.setAttribute("id", gid + "." + aid + "." + v);
                ep.setAttribute("groupId", gid);
                ep.setAttribute("artifactId", aid);
                ep.setAttribute("version", v);

                if (dp.getChild("modules") != null) {
                    for (Element em : dp.getChild("modules").getChildren("module")) {
                        String m = em.getTextNormalize();
                        if (m != null && !"".equalsIgnoreCase(m.trim())) {
                            Element dm;
                            ep.addContent(dm = new Element("module").setAttribute("id", m));
                            procesarPom(dm, new File(f.getParent() + "/" + m + "/pom.xml"));
                        }
                    }
                } else {
                    Element dm;
                    ep.addContent(dm = new Element("module").setAttribute("id", ""));
                    buscarXmls(dm, new File(f.getParent() + "/target/surefire-reports"));
                }

            } else {
                buscarXmls(xml, new File(f.getParent() + "/target/surefire-reports"));
            }
        }
    }

    private static void clean(Document d) {
        clean(d.getRootElement());
    }

    private static void clean(Element e) {
        e.setNamespace(Namespace.NO_NAMESPACE);
        e.getAttributes().forEach(a -> a.setNamespace(Namespace.NO_NAMESPACE));
        e.getChildren().forEach(x -> clean(x));
    }

    private static void buscarXmls(Element xml, File d) throws JDOMException, IOException {
        if (d != null && d.exists() && d.isDirectory()) {
            for (File f : d.listFiles()) {
                procesar(xml, f);
            }
        }
    }

}
