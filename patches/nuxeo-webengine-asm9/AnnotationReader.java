package org.nuxeo.ecm.webengine.rest.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Patched AnnotationReader that uses Opcodes.ASM9 instead of ASM7.
 * <p>
 * This fixes a bug in nuxeo-webengine-rest-2025.0.157.jar where the original
 * AnnotationReader passes Opcodes.ASM7 (458752) to the parent ClassVisitor.
 * When the WebEngine Scanner encounters classes compiled with JDK 17+ sealed
 * class support (e.g., from opensearch-java-2.26.0.jar), the ClassReader calls
 * visitPermittedSubclass() which throws UnsupportedOperationException because
 * the ClassVisitor API version is lower than ASM9.
 * <p>
 * To apply:
 * <pre>
 *   javac -cp "nxserver/bundles/nuxeo-webengine-rest-2025.0.157.jar:nxserver/lib/asm-9.7.1.jar" \
 *         -d . AnnotationReader.java
 *   jar uf nxserver/bundles/nuxeo-webengine-rest-2025.0.157.jar \
 *         org/nuxeo/ecm/webengine/rest/scan/AnnotationReader.class
 * </pre>
 *
 * @see <a href="https://asm.ow2.io/">ASM library</a>
 */
public class AnnotationReader extends ClassVisitor {

    protected Set<String> annotations;
    protected List<String> results;
    protected String cname;

    public AnnotationReader(Set<String> annotations) {
        super(Opcodes.ASM9); // was Opcodes.ASM7 in original
        this.results = new ArrayList<>();
        this.annotations = annotations;
    }

    public String getClassName() {
        return cname.replace('/', '.');
    }

    public String getFileName() {
        return cname;
    }

    public List<String> getResults() {
        return results;
    }

    public boolean hasResults() {
        return !results.isEmpty();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        cname = name;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (annotations.contains(desc)) {
            results.add(desc);
        }
        return null;
    }
}
