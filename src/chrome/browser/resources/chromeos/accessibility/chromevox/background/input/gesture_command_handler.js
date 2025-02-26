// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/**
 * @fileoverview Handles gesture-based commands.
 */
import {AutomationPredicate} from '/common/automation_predicate.js';
import {BridgeHelper} from '/common/bridge_helper.js';
import {EventGenerator} from '/common/event_generator.js';
import {TestImportManager} from '/common/testing/test_import_manager.js';

import {BridgeConstants} from '../../common/bridge_constants.js';
import {EventSourceType} from '../../common/event_source_type.js';
import {GestureCommandData, GestureGranularity} from '../../common/gesture_command_data.js';
import {QueueMode} from '../../common/tts_types.js';
import {ChromeVoxRange} from '../chromevox_range.js';
import {ChromeVoxState} from '../chromevox_state.js';
import {PointerHandler} from '../event/pointer_handler.js';
import {EventSource} from '../event_source.js';
import {ForcedActionPath} from '../forced_action_path.js';
import {Output} from '../output/output.js';

import {CommandHandlerInterface} from './command_handler_interface.js';
import {GestureInterface} from './gesture_interface.js';

const Gesture = chrome.accessibilityPrivate.Gesture;

export class GestureCommandHandler {
  /** @private */
  constructor() {
    /** @private {boolean} */
    this.bypassed_ = false;
    /** @private {GestureGranularity} */
    this.granularity_ = GestureGranularity.LINE;
    /** @private {!PointerHandler} */
    this.pointerHandler_ = new PointerHandler();

    this.init_();
  }

  /** @private */
  init_() {
    chrome.accessibilityPrivate.onAccessibilityGesture.addListener(
        (gesture, x, y) => this.onAccessibilityGesture_(gesture, x, y));
    GestureInterface.granularityGetter = () => this.granularity_;
    GestureInterface.granularitySetter = granularity => this.granularity_ =
        granularity;
  }

  static init() {
    GestureCommandHandler.instance = new GestureCommandHandler();

    BridgeHelper.registerHandler(
        BridgeConstants.GestureCommandHandler.TARGET,
        BridgeConstants.GestureCommandHandler.Action.SET_BYPASS,
        bypassed => GestureCommandHandler.setBypass(bypassed));
  }

  /** @return {boolean} */
  static getEnabled() {
    return !GestureCommandHandler.instance.bypassed_;
  }

  /**
   * Used by LearnMode to capture the events and prevent the standard behavior,
   * in favor of reporting what that would behavior would be.
   * @param {boolean} state
   */
  static setBypass(state) {
    GestureCommandHandler.instance.bypassed_ = state;
  }


  /**
   * Handles accessibility gestures from the touch screen.
   * @param {string} gesture The gesture to handle, based on the
   *     ax::mojom::Gesture enum defined in ui/accessibility/ax_enums.mojom
   * @param {number} x coordinate of gesture
   * @param {number} y coordinate of gesture
   * @private
   */
  onAccessibilityGesture_(gesture, x, y) {
    if (this.bypassed_) {
      return;
    }

    EventSource.set(EventSourceType.TOUCH_GESTURE);

    const actionPath = ForcedActionPath.instance;
    if (gesture !== Gesture.SWIPE_LEFT2 && actionPath &&
        !actionPath.onGesture(gesture)) {
      // ForcedActionPath returns true if this gesture should propagate.
      // Prevent this gesture from propagating if it returns false.
      // Always allow SWIPE_LEFT2 to propagate, since it simulates the escape
      // key.
      return;
    }

    if (gesture === Gesture.TOUCH_EXPLORE) {
      this.pointerHandler_.onTouchMove(x, y);
      return;
    }

    const commandData = GestureCommandData.GESTURE_COMMAND_MAP[gesture];
    if (!commandData) {
      return;
    }

    Output.forceModeForNextSpeechUtterance(QueueMode.FLUSH);

    // Check first for an accelerator action.
    if (commandData.acceleratorAction) {
      chrome.accessibilityPrivate.performAcceleratorAction(
          commandData.acceleratorAction);
      return;
    }

    // Always try to recover the range to the previous valid target which may
    // have been invalidated by touch explore; this recovery omits touch explore
    // explicitly.
    ChromeVoxRange.restoreLastValidRangeIfNeeded();

    // Handle gestures mapped to keys. Global keys are handled in place of
    // commands, and menu key overrides are handled only in menus.
    let key;
    if (ChromeVoxRange.current?.start?.node) {
      let inMenu = false;
      let node = ChromeVoxRange.current.start.node;
      while (node) {
        if (AutomationPredicate.menuItem(node) ||
            (AutomationPredicate.popUpButton(node) && node.state.expanded)) {
          inMenu = true;
          break;
        }
        node = node.parent;
      }

      if (commandData.menuKeyOverride && inMenu) {
        key = commandData.menuKeyOverride;
      }
    }

    key = key ?? commandData.globalKey;
    if (key) {
      EventGenerator.sendKeyPress(key.keyCode, key.modifiers);
      return;
    }

    const command = commandData.command;
    if (command) {
      CommandHandlerInterface.instance.onCommand(command);
    }
  }
}

/** @type {GestureCommandHandler} */
GestureCommandHandler.instance;

TestImportManager.exportForTesting(GestureCommandHandler);
