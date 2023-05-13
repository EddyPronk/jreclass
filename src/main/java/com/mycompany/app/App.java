package com.mycompany.app;



public class App {

    public static void main(String[] args) {
        NodeClassifier reclass = new NodeClassifier();
        String baseDir = "/home/epronk/projects/reclass/examples/";
        var source = new YAMLDataSource();
        reclass.load(baseDir, source);

        //Node node = reclass.getNode("/home/epronk/projects/reclass/examples/nodes/munich/black.example.org.yml");

        String nodeName = "/home/epronk/projects/reclass/examples/nodes/munich/black.example.org.yml";

        reclass.process(nodeName, "output.yml");
    }
}
