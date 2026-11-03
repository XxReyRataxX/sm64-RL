#!/bin/bash
# Scaffold a JavaFX component: creates src file + CSS stub + test stub
# Usage: ./scaffold_javafx.sh <ComponentName> <package>
# Example: ./scaffold_javafx.sh LoginView com.app.views

set -e

COMPONENT="${1:?Usage: $0 <ComponentName> <package>}"
PACKAGE="${2:?Usage: $0 <ComponentName> <package>}"
PACKAGE_PATH="${PACKAGE//.//}"
BASE_DIR="${3:-src/main/java}"
CSS_DIR="${4:-src/main/resources/css}"
TEST_DIR="${5:-src/test/java}"

mkdir -p "${BASE_DIR}/${PACKAGE_PATH}"
mkdir -p "${CSS_DIR}"
mkdir -p "${TEST_DIR}/${PACKAGE_PATH}"

# Java source
cat > "${BASE_DIR}/${PACKAGE_PATH}/${COMPONENT}.java" <<JAVA
package ${PACKAGE};

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

public class ${COMPONENT} extends VBox {

    public ${COMPONENT}() {
        getStyleClass().add("${COMPONENT,,}");
    }

    @FXML
    private void initialize() {
        // TODO: initialize component
    }
}
JAVA

# CSS stub
cat > "${CSS_DIR}/${COMPONENT,,}.css" <<CSS
/* ${COMPONENT} styles */
.${COMPONENT,,} {
    -fx-background-color: #1e1e2e;
    -fx-padding: 16;
}
CSS

# Test stub
cat > "${TEST_DIR}/${PACKAGE_PATH}/${COMPONENT}Test.java" <<JAVA
package ${PACKAGE};

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ${COMPONENT}Test {

    @Test
    void shouldInstantiateWithoutErrors() {
        var component = new ${COMPONENT}();
        assertNotNull(component);
    }
}
JAVA

echo "Scaffolded ${COMPONENT} in ${BASE_DIR}/${PACKAGE_PATH}/"
echo "  CSS  → ${CSS_DIR}/${COMPONENT,,}.css"
echo "  Test → ${TEST_DIR}/${PACKAGE_PATH}/${COMPONENT}Test.java"
