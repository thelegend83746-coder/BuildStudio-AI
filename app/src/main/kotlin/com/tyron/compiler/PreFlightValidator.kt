package com.tyron.compiler

import com.apk.builder.logger.Logger
import com.apk.builder.model.Project
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.regex.Pattern

class PreFlightValidationException(message: String) : Exception(message)

object PreFlightValidator {

    fun validate(project: Project) {
        Logger.log("[Pre-Flight] Starting pre-flight validation...")

        // 1. Validate AndroidManifest.xml
        val manifest = project.manifestFile
        if (!manifest.exists()) {
            throw PreFlightValidationException("Missing AndroidManifest.xml in project: ${manifest.absolutePath}")
        }
        validateXmlSyntax(manifest)

        // 2. Validate all XML files under res/
        val resDir = project.resDir
        if (resDir.exists()) {
            resDir.walkTopDown().filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }.forEach { xmlFile ->
                validateXmlSyntax(xmlFile)
            }
        }

        // 3. Collect declared View IDs from layout XML files
        val declaredIds = mutableSetOf<String>()
        val layoutDir = File(resDir, "layout")
        if (layoutDir.exists()) {
            layoutDir.walkTopDown().filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }.forEach { layoutFile ->
                extractViewIdsFromXml(layoutFile, declaredIds)
            }
        }

        // 4. Verify View IDs referenced in Java/Kotlin sources match declared layout IDs
        val srcDir = project.srcDir
        if (srcDir.exists()) {
            srcDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".java") || it.name.endsWith(".kt")) }.forEach { srcFile ->
                verifySourceViewIds(srcFile, declaredIds)
            }
        }

        Logger.log("[Pre-Flight] ✓ Validation passed! XML syntax and View IDs verified successfully.")
    }

    private fun validateXmlSyntax(file: File) {
        try {
            val content = file.readText()
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next()
            }
        } catch (e: Exception) {
            val errMsg = "XML Syntax Error in ${file.name} (Line ${getLineNumberFromError(e)}):\n${e.message}"
            Logger.log("[Pre-Flight] ❌ $errMsg")
            throw PreFlightValidationException(errMsg)
        }
    }

    private fun extractViewIdsFromXml(file: File, idSet: MutableSet<String>) {
        try {
            val content = file.readText()
            // Match android:id="@+id/my_button" or android:id="@id/my_button"
            val idPattern = Pattern.compile("android:id\\s*=\\s*\"@\\+?id/([a-zA-Z0-9_]+)\"")
            val matcher = idPattern.matcher(content)
            while (matcher.find()) {
                matcher.group(1)?.let { idSet.add(it) }
            }
        } catch (e: Exception) {
            Logger.log("[Pre-Flight] Warning reading ${file.name}: ${e.message}")
        }
    }

    private fun verifySourceViewIds(file: File, declaredIds: Set<String>) {
        try {
            val content = file.readText()

            // Match findViewById(R.id.xxx) or R.id.xxx
            val findPattern = Pattern.compile("R\\.id\\.([a-zA-Z0-9_]+)")
            val matcher = findPattern.matcher(content)

            val missingIds = mutableSetOf<String>()
            while (matcher.find()) {
                val refId = matcher.group(1) ?: continue
                if (!declaredIds.contains(refId)) {
                    missingIds.add(refId)
                }
            }

            if (missingIds.isNotEmpty()) {
                val missingStr = missingIds.joinToString(", ") { "R.id.$it" }
                val errorMsg = "Pre-Flight Validation Failed in ${file.name}:\n" +
                        "Referenced view ID(s) [ $missingStr ] not found in any layout XML (activity_main.xml).\n" +
                        "Please verify the IDs match in your layout file before compiling."
                Logger.log("[Pre-Flight] ❌ $errorMsg")
                throw PreFlightValidationException(errorMsg)
            }
        } catch (e: PreFlightValidationException) {
            throw e
        } catch (e: Exception) {
            Logger.log("[Pre-Flight] Notice: ${e.message}")
        }
    }

    private fun getLineNumberFromError(e: Throwable): String {
        val msg = e.message ?: return "unknown"
        val p = Pattern.compile("line\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(msg)
        return if (m.find()) m.group(1) ?: "unknown" else "unknown"
    }
}
