package csci610.DOTExporter;

import csci610.Graph.Edge;
import csci610.Graph.Graph;
import csci610.Graph.Node;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public class DOTExporter {
    private PrintWriter writer;

    public DOTExporter(PrintWriter w) {
        this.writer = w;
    }

    public void writeHeader() {
        writer.print("digraph control_flow_graph {\n" +
                "    node [shape = rectangle]; entry exit;\n" +
                "    node [shape = circle];\n" +
                "    node [fontname = \"fixed\"];\n" +
                "    edge [fontname = \"fixed\"];\n");
    }

    public void writeFooter() {
        writer.print("\n}");
    }

    public void writeNode(Node n) {
        // nodes don't have properties, don't bother with them
    }

    public void writeEdge(Edge e) {
        writer.printf("    %s -> %s", e.getStart().getNodeEnc(), e.getEnd().getNodeEnc());
        if (e.getLabel() != null) {
            writer.printf(" [label = \"%s\"]", e.getLabel());
        }
        writer.print(";\n");
    }

    public void writeGraph(Graph g) {
        writeHeader();
        for (Node n : g.getNodes()) {
            writeNode(n);
        }
        for (Edge e : g.getEdges()) {
            writeEdge(e);
        }
        writeFooter();
    }

    public void close() throws IOException {
        writer.close();
    }
}
