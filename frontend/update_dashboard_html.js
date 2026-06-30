import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const htmlPath = path.join(__dirname, 'dist', 'index.html');
const javaPath = path.join(__dirname, '..', 'mini-redis', 'src', 'main', 'java', 'com', 'miniredis', 'dashboard', 'DashboardHtml.java');

try {
  let html = fs.readFileSync(htmlPath, 'utf8');

  // Escape backslashes for Java text blocks
  html = html.replace(/\\/g, '\\\\');

  // Escape triple quotes
  html = html.replace(/"""/g, '\\"\\"\\"');

  // Split into chunks of 10,000 characters
  const chunks = [];
  const chunkSize = 10000;
  for (let i = 0; i < html.length; i += chunkSize) {
    chunks.push(html.substring(i, i + chunkSize));
  }

  let javaContent = `package com.miniredis.dashboard;

public class DashboardHtml {
`;

  chunks.forEach((chunk, index) => {
    javaContent += `    private static final String PART${index + 1} = """\n${chunk}""";\n\n`;
  });

  javaContent += `    public static String getHtml() {\n`;
  javaContent += `        return new StringBuilder()\n`;
  chunks.forEach((_, index) => {
    javaContent += `            .append(PART${index + 1})\n`;
  });
  javaContent += `            .toString();\n`;
  javaContent += `    }\n}\n`;

  fs.writeFileSync(javaPath, javaContent, 'utf8');
  console.log(`✓ Successfully updated DashboardHtml.java with ${chunks.length} HTML chunks using StringBuilder!`);
} catch (err) {
  console.error('Error updating DashboardHtml.java:', err);
  process.exit(1);
}
