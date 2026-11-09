import XCTest
import UIKit

final class LaserSketchUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["LASERSKETCH_TEST_ID"] = UUID().uuidString
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    private func button(_ text: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    private func createSketch() {
        let new = button("New sketch")
        XCTAssertTrue(new.waitForExistence(timeout: 15))
        new.tap()
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("iOS regression")
        // A fresh simulator can present Apple's slide-to-type introduction.
        let keyboardIntroduction = app.buttons["Continue"]
        if keyboardIntroduction.exists { keyboardIntroduction.tap() }
        XCTAssertTrue(NSPredicate(format: "label == %@", "iOS regression").evaluate(with: field))
        button("Apply").tap()
        XCTAssertTrue(button("Settings").waitForExistence(timeout: 10))
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 5))
    }

    func testHomeLanguageSwitchAndPersistence() throws {
        createSketch()
        button("Save and close").tap()
        XCTAssertTrue(button("Back").waitForExistence(timeout: 10))
        button("Back").tap()
        XCTAssertTrue(button("New sketch").waitForExistence(timeout: 10))
        let choices = [("简体中文", "设置", "语言", "新建草图"),
                       ("繁體中文", "設定", "語言", "新建草圖"),
                       ("日本語", "設定", "言語", "新規スケッチ"),
                       ("한국어", "설정", "언어", "새 스케치"),
                       ("English", "Settings", "Language", "New sketch")]
        var settings = "Settings"
        var language = "Language"
        for (choice, newSettings, newLanguage, newSketch) in choices {
            button(settings).tap()
            button(language).tap()
            button(choice).tap()
            XCTAssertTrue(button(newSketch).waitForExistence(timeout: 5))
            app.terminate()
            app.launch()
            XCTAssertTrue(button(newSketch).waitForExistence(timeout: 10))
            XCTAssertTrue(app.staticTexts["iOS regression"].exists)
            settings = newSettings
            language = newLanguage
        }
        button(settings).tap()
        button(language).tap()
        button("Follow system").tap()
        XCTAssertTrue(button("New sketch").waitForExistence(timeout: 5))
    }

    func testWallPlacementUndoAndSavedProjectSurvivesRelaunch() throws {
        createSketch()
        button("Wall").tap()
        let origin = app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.48))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.48))
        let before = app.screenshot().image.cgImage!
        origin.tap()
        end.tap()
        let after = app.screenshot().image.cgImage!
        let region = CGRect(x: CGFloat(before.width) * 0.3, y: CGFloat(before.height) * 0.46,
                            width: CGFloat(before.width) * 0.3, height: CGFloat(before.height) * 0.04)
        let oldPixels = before.cropping(to: region)!.dataProvider!.data! as Data
        let newPixels = after.cropping(to: region)!.dataProvider!.data! as Data
        XCTAssertNotEqual(oldPixels, newPixels, "Committed geometry must repaint before navigation or zoom")
        XCTAssertTrue(app.staticTexts["Walls: 1 · Openings: 0 · Columns: 0"].waitForExistence(timeout: 5))
        button("Undo").tap()
        XCTAssertTrue(app.staticTexts["Walls: 0 · Openings: 0 · Columns: 0"].waitForExistence(timeout: 5))
        button("Redo").tap()
        button("Finish").tap()
        button("Save and close").tap()
        XCTAssertTrue(button("Floor sketch").waitForExistence(timeout: 10))
        button("Back").tap()
        XCTAssertTrue(app.staticTexts["iOS regression"].waitForExistence(timeout: 10))
        app.terminate()
        app.launch()
        let project = app.staticTexts["iOS regression"]
        XCTAssertTrue(project.waitForExistence(timeout: 10))
        project.tap()
        button("Floor sketch").tap()
        XCTAssertTrue(button("Settings").waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Walls: 1 · Openings: 0 · Columns: 0"].waitForExistence(timeout: 5))
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Restored shared sketch"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testDimensionDrivesWallUsingSharedCanvas() throws {
        createSketch()
        button("Wall").tap()
        let origin = app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.48))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.48))
        origin.tap(); end.tap()
        button("Dimension").tap()
        XCTAssertTrue(button("Dimension").isSelected)
        origin.tap(); end.tap()
        let dimension = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.64))
        dimension.tap()
        let previewAttachment = XCTAttachment(screenshot: app.screenshot())
        previewAttachment.name = "Dimension placement"
        previewAttachment.lifetime = .keepAlways
        add(previewAttachment)
        XCTAssertTrue(app.staticTexts["Dimensions: 1"].waitForExistence(timeout: 5))
        button("Select").tap()
        XCTAssertTrue(button("Select").isSelected)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.48)).tap()
        let beforeDrive = app.screenshot().image.cgImage!
        dimension.tap()
        XCTAssertTrue(app.staticTexts["Distance (mm)"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        // The shared distance dialog selects its entire initial value on focus.
        app.typeText("800")
        button("Apply").tap()
        let driven = XCTAttachment(screenshot: app.screenshot())
        driven.name = "Drive result"
        driven.lifetime = .keepAlways
        add(driven)
        XCTAssertFalse(app.staticTexts["Invalid value or geometry conflict. Adjust the value and retry."].exists)
        XCTAssertTrue(app.staticTexts["Dimensions: 1"].exists)
        let afterDrive = app.screenshot().image.cgImage!
        let edge = CGRect(x: CGFloat(beforeDrive.width) * 0.72, y: CGFloat(beforeDrive.height) * 0.47,
                          width: CGFloat(beforeDrive.width) * 0.03, height: CGFloat(beforeDrive.height) * 0.02)
        XCTAssertNotEqual(beforeDrive.cropping(to: edge)!.dataProvider!.data! as Data,
                          afterDrive.cropping(to: edge)!.dataProvider!.data! as Data,
                          "Driving from 1000 mm to 800 mm must move the actual wall edge")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Dimension driven wall"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testCreationAndDrawingSettingsValidateDrafts() throws {
        createSketch()
        button("Wall").tap()
        button("Thickness (mm)").tap()
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        let length = (field.value as? String)?.count ?? 3
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: length) + "0")
        button("Apply").tap()
        XCTAssertTrue(app.staticTexts["Invalid value or geometry conflict. Adjust the value and retry."].waitForExistence(timeout: 5))
        button("Cancel").tap()
        button("Settings").tap()
        XCTAssertTrue(app.staticTexts["Grid spacing (mm)"].waitForExistence(timeout: 5) || app.textFields["Grid spacing (mm)"].exists)
        button("Cancel").tap()
        XCTAssertTrue(button("Save and close").exists)
    }

    func testSystemPhotoPickerAndAnnotationFlow() throws {
        guard Bundle(for: LaserSketchUITests.self).object(forInfoDictionaryKey: "LaserSketchSyntheticPhotoReady") as? String == "1" else {
            throw XCTSkip("Run scripts/test-ios.sh to use an isolated simulator with a synthetic photo.")
        }
        XCTAssertTrue(button("Add photo").waitForExistence(timeout: 15))
        button("Add photo").tap()
        let thumbnail = app.images.matching(identifier: "PXGGridLayout-Info").firstMatch
        XCTAssertTrue(thumbnail.waitForExistence(timeout: 45))
        thumbnail.tap()
        XCTAssertTrue(button("Share image").waitForExistence(timeout: 15))
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.36))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.7, dy: 0.4))
        start.press(forDuration: 0.1, thenDragTo: end)
        XCTAssertTrue(app.staticTexts["Annotations: 1"].waitForExistence(timeout: 5))
        button("Text").tap()
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.38)).tap()
        XCTAssertTrue(app.staticTexts["Annotations: 2"].waitForExistence(timeout: 5))
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("Photo fixture")
        button("Angle").tap()
        for point in [CGVector(dx: 0.2, dy: 0.32), CGVector(dx: 0.4, dy: 0.4), CGVector(dx: 0.6, dy: 0.32)] {
            app.coordinate(withNormalizedOffset: point).tap()
        }
        XCTAssertTrue(app.staticTexts["Annotations: 3"].waitForExistence(timeout: 5))
        button("Area").tap()
        for point in [CGVector(dx: 0.2, dy: 0.35), CGVector(dx: 0.4, dy: 0.45), CGVector(dx: 0.6, dy: 0.35)] {
            app.coordinate(withNormalizedOffset: point).tap()
        }
        button("Finish").tap()
        XCTAssertTrue(app.staticTexts["Annotations: 4"].waitForExistence(timeout: 5))
        button("Delete").tap()
        XCTAssertTrue(app.staticTexts["Annotations: 3"].waitForExistence(timeout: 5))
        button("Save to Photos").tap()
        XCTAssertTrue(app.staticTexts["Saved to Photos"].waitForExistence(timeout: 15))
        button("Save and close").tap()
        XCTAssertTrue(button("Photo 1").waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        let project = app.staticTexts["Photo annotations"]
        XCTAssertTrue(project.waitForExistence(timeout: 10))
        project.tap()
        button("Photo 1").tap()
        XCTAssertTrue(app.staticTexts["Annotations: 3"].waitForExistence(timeout: 10))
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Restored photo annotation"
        attachment.lifetime = .keepAlways
        add(attachment)
    }


    func testImportFromSystemFilePicker() throws {
        guard Bundle(for: LaserSketchUITests.self).object(forInfoDictionaryKey: "LaserSketchSyntheticPhotoReady") as? String == "1" else {
            throw XCTSkip("Run scripts/test-ios.sh to use isolated document fixtures.")
        }
        XCTAssertTrue(button("Import project").waitForExistence(timeout: 15))
        button("Import project").tap()
        _ = app.navigationBars.firstMatch.waitForExistence(timeout: 10)
        app.tabBars.buttons["Browse"].tap()
        app.tabBars.buttons["Browse"].tap()
        let local = app.cells["DOC.sidebar.item.On My iPhone"]
        XCTAssertTrue(local.waitForExistence(timeout: 10))
        local.tap()
        let folder = app.collectionViews.cells.matching(NSPredicate(format: "label BEGINSWITH %@", "LaserSketchTest")).firstMatch
        XCTAssertTrue(folder.waitForExistence(timeout: 10))
        folder.tap()
        let fixture = app.collectionViews.cells.matching(NSPredicate(format: "label BEGINSWITH %@", "CrossPlatformFixture")).firstMatch
        XCTAssertTrue(fixture.waitForExistence(timeout: 10))
        fixture.tap()
        XCTAssertTrue(button("Floor sketch").waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["跨平台样本"].firstMatch.exists)
        button("Floor sketch").tap()
        XCTAssertTrue(app.staticTexts["Walls: 0 · Openings: 0 · Columns: 0"].waitForExistence(timeout: 10))
        button("Save and close").tap()
        button("Back").tap()
        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["跨平台样本"].firstMatch.waitForExistence(timeout: 10))
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Imported Android project survives restart"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testNativeProjectExchangeSheets() throws {
        createSketch()
        button("Save and close").tap()
        XCTAssertTrue(button("Export project").waitForExistence(timeout: 10))
        button("Export project").tap()
        XCTAssertTrue(app.cells["Copy"].waitForExistence(timeout: 10))
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Native project export"
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertTrue(app.cells["Copy"].exists)
    }

    func testSimulatorBluetoothScanReportsUnavailable() throws {
        XCTAssertTrue(button("Devices").waitForExistence(timeout: 15))
        button("Devices").tap()
        button("Scan devices").tap()
        XCTAssertTrue(app.staticTexts["Bluetooth is off or unavailable."].firstMatch.waitForExistence(timeout: 15))
    }

    func testMeasurementAndDevicePanelsAreReachableWithoutRequestingBluetooth() throws {
        XCTAssertTrue(button("New sketch").waitForExistence(timeout: 15))
        button("Measurements").tap()
        XCTAssertTrue(app.staticTexts["No measurements"].waitForExistence(timeout: 5))
        XCTAssertFalse(button("Measure").isEnabled)
        button("Devices").tap()
        XCTAssertTrue(button("Scan devices").waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Disconnected"].exists)
    }
}
