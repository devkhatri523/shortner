package com.url.shortner.orchestrator.test;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.stream.Stream;

/** Maven Surefire XML, not an LLM, is the source of truth for pass/fail. */
@Component
public class SurefireReportReader {
    public record Summary(int total, int failed, String failures) {}

    public Summary parse(Path workspace) throws Exception {
        Path dir = workspace.resolve("url-shortener-service/target/surefire-reports");
        if (!Files.isDirectory(dir)) return new Summary(0, 0, "No Surefire XML reports found");
        int total = 0, failed = 0;
        StringBuilder errors = new StringBuilder();
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path file : paths.filter(p -> p.getFileName().toString().startsWith("TEST-") &&
                    p.getFileName().toString().endsWith(".xml")).toList()) {
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                var doc = factory.newDocumentBuilder().parse(file.toFile());
                Element suite = doc.getDocumentElement();
                total += number(suite.getAttribute("tests"));
                failed += number(suite.getAttribute("failures")) + number(suite.getAttribute("errors"));
                var testCases = suite.getElementsByTagName("testcase");
                for (int i = 0; i < testCases.getLength() && errors.length() < 10000; i++) {
                    Element testCase = (Element) testCases.item(i);
                    var children = testCase.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        if (!(children.item(j) instanceof Element elt)) continue;
                        if (elt.getTagName().equals("failure") || elt.getTagName().equals("error")) {
                            errors.append(testCase.getAttribute("classname"))
                                  .append("#").append(testCase.getAttribute("name"))
                                  .append(" : ").append(elt.getAttribute("message")).append("\n")
                                  .append(elt.getTextContent(), 0,
                                          Math.min(elt.getTextContent().length(), 1400)).append("\n");
                        }
                    }
                }
            }
        }
        return new Summary(total, failed, errors.toString());
    }

    private static int number(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { return 0; }
    }
}
