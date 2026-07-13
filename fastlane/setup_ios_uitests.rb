#!/usr/bin/env ruby
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 HelloMeds Contributors
#
# One-time bootstrap: adds a `HelloMedsUITests` UI Testing Bundle target to
# `iosApp/HelloMeds.xcodeproj` if missing, with ScreenshotUITests.swift,
# SnapshotHelper.swift, and AccessibilityAuditTests.swift as compile sources.
#
# Run via `./hm screenshots ios` — invoked automatically when the target is
# missing.

require "xcodeproj"

PROJECT_PATH = File.expand_path("../iosApp/HelloMeds.xcodeproj", __dir__)
UITEST_DIR_NAME = "HelloMedsUITests"
TARGET_NAME = "HelloMedsUITests"
HOST_TARGET_NAME = "HelloMeds"
SCHEME_NAME = "HelloMeds"
BUNDLE_ID = "me.juliana.HelloMeds.UITests"
DEPLOYMENT_TARGET = "17.0"

UITEST_SOURCES = %w[
  ScreenshotUITests.swift
  SnapshotHelper.swift
  AccessibilityAuditTests.swift
].freeze

project = Xcodeproj::Project.open(PROJECT_PATH)

host_target = project.targets.find { |t| t.name == HOST_TARGET_NAME }
abort "[setup-ios-uitests] Host target '#{HOST_TARGET_NAME}' not found" unless host_target

uitest_target = project.targets.find { |t| t.name == TARGET_NAME }

if uitest_target
  puts "[setup-ios-uitests] Target '#{TARGET_NAME}' already exists — reconciling build settings."
else
  uitest_target = project.new_target(
    :ui_test_bundle,
    TARGET_NAME,
    :ios,
    DEPLOYMENT_TARGET,
    nil,
    :swift,
  )

  uitest_target.add_dependency(host_target)

  group = project.main_group.find_subpath(UITEST_DIR_NAME, true)
  group.set_source_tree("<group>")
  group.set_path(UITEST_DIR_NAME)

  UITEST_SOURCES.each do |source|
    source_path = File.join(File.dirname(PROJECT_PATH), UITEST_DIR_NAME, source)
    next unless File.exist?(source_path)
    file_ref = group.new_file(source)
    uitest_target.add_file_references([file_ref])
  end

  project.root_object.attributes["TargetAttributes"] ||= {}
  project.root_object.attributes["TargetAttributes"][uitest_target.uuid] = {
    "CreatedOnToolsVersion" => "26.3",
    "TestTargetID" => host_target.uuid,
  }

  puts "[setup-ios-uitests] Added '#{TARGET_NAME}' target with #{UITEST_SOURCES.size} source files."
end

# `Xcodeproj::Project#new_target` doesn't populate PRODUCT_NAME; without it
# the linker writes to a literal ".xctest" and crashes on duplicate output.
# Applied unconditionally so older targets created without this fix get repaired.
uitest_target.build_configurations.each do |config|
  config.build_settings["PRODUCT_NAME"] = "$(TARGET_NAME)"
  config.build_settings["PRODUCT_BUNDLE_IDENTIFIER"] = BUNDLE_ID
  config.build_settings["TEST_TARGET_NAME"] = HOST_TARGET_NAME
  config.build_settings["SWIFT_VERSION"] = "5.0"
  config.build_settings["TARGETED_DEVICE_FAMILY"] = "1,2"
  config.build_settings["GENERATE_INFOPLIST_FILE"] = "YES"
  config.build_settings["CODE_SIGN_STYLE"] = "Automatic"
  config.build_settings["IPHONEOS_DEPLOYMENT_TARGET"] = DEPLOYMENT_TARGET
end

project.save

# Register the UITest target in the shared HelloMeds scheme's TestAction so
# xcodebuild test (and fastlane snapshot) can find it.
scheme_path = Xcodeproj::XCScheme.shared_data_dir(PROJECT_PATH) + "#{SCHEME_NAME}.xcscheme"
abort "[setup-ios-uitests] Scheme '#{SCHEME_NAME}' not found at #{scheme_path}" unless File.exist?(scheme_path)

scheme = Xcodeproj::XCScheme.new(scheme_path)
already_registered = scheme.test_action.testables.any? { |t|
  t.buildable_references.any? { |b| b.target_name == TARGET_NAME }
}

unless already_registered
  testable = Xcodeproj::XCScheme::TestAction::TestableReference.new(uitest_target)
  scheme.test_action.add_testable(testable)
end

# Without a MacroExpansion pointing at the host app, `XCUIApplication()` falls
# back to the test bundle's own ID and the simulator refuses to launch it.
unless scheme.test_action.xml_element.elements["MacroExpansion"]
  scheme.test_action.add_macro_expansion(
    Xcodeproj::XCScheme::MacroExpansion.new(host_target),
  )
end

scheme.save!
puts "[setup-ios-uitests] Scheme '#{SCHEME_NAME}' wired for '#{TARGET_NAME}' with host '#{HOST_TARGET_NAME}'."
puts "[setup-ios-uitests] Commit iosApp/HelloMeds.xcodeproj/ changes when ready."
