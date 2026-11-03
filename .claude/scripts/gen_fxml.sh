#!/bin/bash
# Generate a minimal FXML file for a JavaFX controller
# Usage: ./gen_fxml.sh <ControllerClass> <package> [output_dir]

set -e

CONTROLLER="${1:?Usage: $0 <ControllerClass> <package> [output_dir]}"
PACKAGE="${2:?Usage: $0 <ControllerClass> <package> [output_dir]}"
OUT_DIR="${3:-src/main/resources/fxml}"

mkdir -p "${OUT_DIR}"
FILENAME="${CONTROLLER,,}.fxml"

cat > "${OUT_DIR}/${FILENAME}" <<FXML
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.VBox?>
<?import javafx.scene.control.Label?>

<VBox xmlns="http://javafx.com/javafx"
      xmlns:fx="http://javafx.com/fxml"
      fx:controller="${PACKAGE}.${CONTROLLER}"
      spacing="10" alignment="CENTER"
      style="-fx-padding: 20; -fx-background-color: #1e1e2e;">

    <Label text="${CONTROLLER}" styleClass="title-label"/>

</VBox>
FXML

echo "Generated FXML → ${OUT_DIR}/${FILENAME}"
