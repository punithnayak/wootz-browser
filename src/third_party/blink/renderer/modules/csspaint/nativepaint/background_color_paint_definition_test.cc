// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "third_party/blink/renderer/modules/csspaint/nativepaint/background_color_paint_definition.h"

#include "base/memory/scoped_refptr.h"
#include "testing/gmock/include/gmock/gmock.h"
#include "third_party/blink/public/platform/scheduler/test/renderer_scheduler_test_support.h"
#include "third_party/blink/renderer/bindings/core/v8/v8_union_cssnumericvalue_double.h"
#include "third_party/blink/renderer/core/animation/document_timeline.h"
#include "third_party/blink/renderer/core/animation/element_animations.h"
#include "third_party/blink/renderer/core/animation/inert_effect.h"
#include "third_party/blink/renderer/core/animation/keyframe_effect.h"
#include "third_party/blink/renderer/core/animation/keyframe_effect_model.h"
#include "third_party/blink/renderer/core/animation/string_keyframe.h"
#include "third_party/blink/renderer/core/animation/timing.h"
#include "third_party/blink/renderer/core/css/background_color_paint_image_generator.h"
#include "third_party/blink/renderer/core/css/resolver/style_resolver.h"
#include "third_party/blink/renderer/core/dom/element.h"
#include "third_party/blink/renderer/core/dom/node_computed_style.h"
#include "third_party/blink/renderer/core/execution_context/security_context.h"
#include "third_party/blink/renderer/core/frame/local_frame_view.h"
#include "third_party/blink/renderer/core/style/computed_style.h"
#include "third_party/blink/renderer/core/testing/core_unit_test_helper.h"
#include "third_party/blink/renderer/platform/bindings/exception_state.h"
#include "third_party/blink/renderer/platform/graphics/bitmap_image.h"
#include "third_party/blink/renderer/platform/graphics/color.h"
#include "third_party/blink/renderer/platform/graphics/platform_paint_worklet_layer_painter.h"

namespace blink {

class FakeBackgroundColorPaintImageGenerator
    : public BackgroundColorPaintImageGenerator {
 public:
  FakeBackgroundColorPaintImageGenerator() = default;

  scoped_refptr<Image> Paint(const gfx::SizeF& container_size,
                             const Node* node) override {
    return BitmapImage::Create();
  }

  Animation* GetAnimationIfCompositable(const Element* element) override {
    return BackgroundColorPaintDefinition::GetAnimationIfCompositable(element);
  }

  void Shutdown() override {}
};

class BackgroundColorPaintDefinitionTest : public RenderingTest {
 public:
  BackgroundColorPaintDefinitionTest() = default;
  ~BackgroundColorPaintDefinitionTest() override = default;

  void SetUp() override {
    EnableCompositing();
    RenderingTest::SetUp();
    FakeBackgroundColorPaintImageGenerator* generator =
        MakeGarbageCollected<FakeBackgroundColorPaintImageGenerator>();
    GetDocument().GetFrame()->SetBackgroundColorPaintImageGeneratorForTesting(
        generator);
  }

  // Crash testing of BackgroundColorPaintDefinition::Paint
  void RunPaintForTest(const Vector<Color>& animated_colors,
                       const Vector<double>& offsets,
                       const CompositorPaintWorkletJob::AnimatedPropertyValues&
                           property_values) {
    BackgroundColorPaintDefinition definition;
    definition.PaintForTest(animated_colors, offsets, property_values);
  }

 private:
  Persistent<FakeBackgroundColorPaintImageGenerator> paint_image_generator_;
};

// Test the case where there is a background-color animation with two simple
// keyframes that will not fall back to main.
TEST_F(BackgroundColorPaintDefinitionTest, SimpleBGColorAnimationNotFallback) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML(R"HTML(
    <div id ="target" style="width: 100px; height: 100px">
    </div>
  )HTML");

  Timing timing;
  timing.iteration_duration = ANIMATION_TIME_DELTA_FROM_SECONDS(30);

  CSSPropertyID property_id = CSSPropertyID::kBackgroundColor;
  Persistent<StringKeyframe> start_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  start_keyframe->SetCSSPropertyValue(
      property_id, "red", SecureContextMode::kInsecureContext, nullptr);
  Persistent<StringKeyframe> end_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  end_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);

  StringKeyframeVector keyframes;
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);

  auto* model = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);
  model->SetComposite(EffectModel::kCompositeReplace);

  Element* element = GetElementById("target");
  NonThrowableExceptionState exception_state;
  DocumentTimeline* timeline =
      MakeGarbageCollected<DocumentTimeline>(&GetDocument());
  Animation* animation = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model, timing), timeline,
      exception_state);
  UpdateAllLifecyclePhasesForTest();
  animation->play();

  EXPECT_TRUE(element->GetElementAnimations());
  EXPECT_EQ(element->GetElementAnimations()->Animations().size(), 1u);
  EXPECT_EQ(element->GetElementAnimations()->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNeedsRepaint);

  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(element->GetElementAnimations()->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kComposited);
}

TEST_F(BackgroundColorPaintDefinitionTest, FallbackWithPixelMovingFilter) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML(R"HTML(
    <div id="grandparent">
      <div id="parent">
        <div id ="target" style="width: 100px; height: 100px">
        </div>
      </div>
    </div>
  )HTML");
  UpdateAllLifecyclePhasesForTest();

  auto* settings = GetDocument().GetSettings();
  EXPECT_TRUE(settings->GetAcceleratedCompositingEnabled());

  Timing timing;
  timing.iteration_duration = ANIMATION_TIME_DELTA_FROM_SECONDS(30);

  CSSPropertyID property_id = CSSPropertyID::kBackgroundColor;
  Persistent<StringKeyframe> start_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  start_keyframe->SetCSSPropertyValue(
      property_id, "red", SecureContextMode::kInsecureContext, nullptr);
  Persistent<StringKeyframe> end_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  end_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);

  StringKeyframeVector keyframes;
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);

  auto* model = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);
  model->SetComposite(EffectModel::kCompositeReplace);

  Element* element = GetElementById("target");
  NonThrowableExceptionState exception_state;
  DocumentTimeline* timeline =
      MakeGarbageCollected<DocumentTimeline>(&GetDocument());
  Animation* animation = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model, timing), timeline,
      exception_state);
  UpdateAllLifecyclePhasesForTest();
  animation->play();

  EXPECT_EQ(BackgroundColorPaintDefinition::GetAnimationIfCompositable(element),
            animation);

  element->SetNeedsAnimationStyleRecalc();
  GetDocument().UpdateStyleAndLayoutTree();
  EXPECT_TRUE(element->ComputedStyleRef().HasCurrentBackgroundColorAnimation());
  ElementAnimations* element_animations = element->GetElementAnimations();
  EXPECT_TRUE(element_animations);
  EXPECT_EQ(element_animations->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNeedsRepaint);

  // Run paint.
  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(element_animations->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kComposited);

  Element* parent = GetElementById("parent");
  CSSStyleDeclaration* inline_style = parent->style();

  // The contrast filter is compatible with compositing the background color
  // as it does not affect the damage rect.
  inline_style->setProperty(
      parent->GetExecutionContext(),
      CSSPropertyName(CSSPropertyID::kFilter).ToAtomicString(),
      "contrast(200%)", "", ASSERT_NO_EXCEPTION);
  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(BackgroundColorPaintDefinition::GetAnimationIfCompositable(element),
            animation);
  element_animations = element->GetElementAnimations();
  EXPECT_EQ(element_animations->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kComposited);

  // The blur filter is incompatible with compositing the background color since
  // the damage rect must expand to accommodate the filter.
  inline_style->setProperty(
      parent->GetExecutionContext(),
      CSSPropertyName(CSSPropertyID::kFilter).ToAtomicString(), "blur(5px)", "",
      ASSERT_NO_EXCEPTION);
  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(BackgroundColorPaintDefinition::GetAnimationIfCompositable(element),
            nullptr);
  element_animations = element->GetElementAnimations();
  EXPECT_EQ(element_animations->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNotComposited);

  // Reset.
  inline_style->setProperty(
      parent->GetExecutionContext(),
      CSSPropertyName(CSSPropertyID::kFilter).ToAtomicString(), "none", "",
      ASSERT_NO_EXCEPTION);
  animation->cancel();
  animation->play();
  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(BackgroundColorPaintDefinition::GetAnimationIfCompositable(element),
            animation);
  element_animations = element->GetElementAnimations();
  EXPECT_EQ(element_animations->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kComposited);

  // Add blur to grandparent.
  Element* grandparent = GetElementById("grandparent");
  inline_style = grandparent->style();
  inline_style->setProperty(
      parent->GetExecutionContext(),
      CSSPropertyName(CSSPropertyID::kFilter).ToAtomicString(), "blur(5px)", "",
      ASSERT_NO_EXCEPTION);
  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(BackgroundColorPaintDefinition::GetAnimationIfCompositable(element),
            nullptr);
  element_animations = element->GetElementAnimations();
  EXPECT_EQ(element_animations->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNotComposited);
}

// Test the case when there is no animation attached to the element.
TEST_F(BackgroundColorPaintDefinitionTest, FallbackToMainNoAnimation) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML(R"HTML(
    <div id ="target" style="width: 100px; height: 100px">
    </div>
  )HTML");
  Element* element = GetElementById("target");
  EXPECT_FALSE(element->GetElementAnimations());
}

// Test the case where the composite mode is not replace.
TEST_F(BackgroundColorPaintDefinitionTest, FallbackToMainCompositeAccumulate) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML(R"HTML(
    <div id ="target" style="width: 100px; height: 100px">
    </div>
  )HTML");

  Timing timing;
  timing.iteration_duration = ANIMATION_TIME_DELTA_FROM_SECONDS(30);

  CSSPropertyID property_id = CSSPropertyID::kBackgroundColor;
  Persistent<StringKeyframe> start_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  start_keyframe->SetCSSPropertyValue(
      property_id, "red", SecureContextMode::kInsecureContext, nullptr);
  Persistent<StringKeyframe> end_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  end_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);

  StringKeyframeVector keyframes;
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);

  auto* model = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);
  model->SetComposite(EffectModel::kCompositeAccumulate);

  Element* element = GetElementById("target");
  NonThrowableExceptionState exception_state;
  DocumentTimeline* timeline =
      MakeGarbageCollected<DocumentTimeline>(&GetDocument());
  Animation* animation = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model, timing), timeline,
      exception_state);
  UpdateAllLifecyclePhasesForTest();
  animation->play();

  EXPECT_TRUE(element->GetElementAnimations());
  EXPECT_EQ(element->GetElementAnimations()->Animations().size(), 1u);
  EXPECT_EQ(element->GetElementAnimations()->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNeedsRepaint);
  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(element->GetElementAnimations()->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNotComposited);
}

TEST_F(BackgroundColorPaintDefinitionTest, MultipleAnimationsFallback) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML(R"HTML(
    <div id ="target" style="width: 100px; height: 100px">
    </div>
  )HTML");

  Timing timing;
  timing.iteration_duration = ANIMATION_TIME_DELTA_FROM_SECONDS(30);

  CSSPropertyID property_id = CSSPropertyID::kBackgroundColor;
  Persistent<StringKeyframe> start_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  start_keyframe->SetCSSPropertyValue(
      property_id, "red", SecureContextMode::kInsecureContext, nullptr);
  Persistent<StringKeyframe> end_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  end_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);

  StringKeyframeVector keyframes;
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);
  auto* model1 = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);

  Element* element = GetElementById("target");
  NonThrowableExceptionState exception_state;
  DocumentTimeline* timeline =
      MakeGarbageCollected<DocumentTimeline>(&GetDocument());
  Animation* animation1 = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model1, timing), timeline,
      exception_state);

  start_keyframe->SetCSSPropertyValue(
      property_id, "blue", SecureContextMode::kInsecureContext, nullptr);
  end_keyframe->SetCSSPropertyValue(
      property_id, "yellow", SecureContextMode::kInsecureContext, nullptr);
  keyframes.clear();
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);
  auto* model2 = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);
  Animation* animation2 = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model2, timing), timeline,
      exception_state);
  UpdateAllLifecyclePhasesForTest();
  animation1->play();
  animation2->play();

  // Two active background-color animations, fall back to main.
  EXPECT_TRUE(element->GetElementAnimations());
  EXPECT_EQ(element->GetElementAnimations()->Animations().size(), 2u);
  EXPECT_EQ(element->GetElementAnimations()->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNeedsRepaint);
  UpdateAllLifecyclePhasesForTest();
  EXPECT_EQ(element->GetElementAnimations()->CompositedBackgroundColorStatus(),
            ElementAnimations::CompositedPaintStatus::kNotComposited);
}

// Test that paint is invalidated in the case that a second background color
// animation is added.
TEST_F(BackgroundColorPaintDefinitionTest,
       TriggerRepaintCompositedToNonComposited) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML(R"HTML(
    <div id ="target" style="width: 100px; height: 100px">
    </div>
  )HTML");

  Timing timing;
  timing.iteration_duration = ANIMATION_TIME_DELTA_FROM_SECONDS(30);

  CSSPropertyID property_id = CSSPropertyID::kBackgroundColor;
  Persistent<StringKeyframe> start_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  start_keyframe->SetCSSPropertyValue(
      property_id, "red", SecureContextMode::kInsecureContext, nullptr);
  Persistent<StringKeyframe> end_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  end_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);

  StringKeyframeVector keyframes;
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);
  auto* model1 = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);

  Element* element = GetElementById("target");
  StyleRecalcContext style_recalc_context;
  style_recalc_context.old_style = element->GetComputedStyle();
  const ComputedStyle* style = GetDocument().GetStyleResolver().ResolveStyle(
      element, style_recalc_context);
  EXPECT_FALSE(style->HasCurrentBackgroundColorAnimation());

  NonThrowableExceptionState exception_state;
  DocumentTimeline* timeline =
      MakeGarbageCollected<DocumentTimeline>(&GetDocument());
  Animation* animation1 = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model1, timing), timeline,
      exception_state);
  animation1->play();
  ASSERT_TRUE(element->GetElementAnimations());
  EXPECT_EQ(element->GetElementAnimations()->Animations().size(), 1u);
  style = GetDocument().GetStyleResolver().ResolveStyle(element,
                                                        style_recalc_context);
  // Previously no background-color animation, now it has. This should trigger
  // a repaint, see ComputedStyle::UpdatePropertySpecificDifferences().
  EXPECT_TRUE(style->HasCurrentBackgroundColorAnimation());

  start_keyframe->SetCSSPropertyValue(
      property_id, "blue", SecureContextMode::kInsecureContext, nullptr);
  end_keyframe->SetCSSPropertyValue(
      property_id, "yellow", SecureContextMode::kInsecureContext, nullptr);
  keyframes.clear();
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);
  auto* model2 = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);
  Animation* animation2 = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model2, timing), timeline,
      exception_state);
  animation1->play();
  animation2->play();

  ASSERT_TRUE(element->GetElementAnimations());
  EXPECT_EQ(element->GetElementAnimations()->Animations().size(), 2u);
  GetDocument().View()->UpdateLifecycleToCompositingInputsClean(
      DocumentUpdateReason::kTest);
  EXPECT_TRUE(element->GetLayoutObject()->ShouldDoFullPaintInvalidation());
  EXPECT_TRUE(element->ComputedStyleRef().HasCurrentBackgroundColorAnimation());
}

// Test that paint is invalidated when an animation's keyframe is changed
TEST_F(BackgroundColorPaintDefinitionTest, TriggerRepaintChangedKeyframe) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML(R"HTML(
    <div id ="target" style="width: 100px; height: 100px">
    </div>
  )HTML");

  Timing timing;
  timing.iteration_duration = ANIMATION_TIME_DELTA_FROM_SECONDS(30);

  CSSPropertyID property_id = CSSPropertyID::kBackgroundColor;
  Persistent<StringKeyframe> start_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  start_keyframe->SetCSSPropertyValue(
      property_id, "red", SecureContextMode::kInsecureContext, nullptr);
  Persistent<StringKeyframe> end_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  end_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);

  StringKeyframeVector keyframes;
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);
  auto* model = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);

  Element* element = GetElementById("target");
  StyleRecalcContext style_recalc_context;
  style_recalc_context.old_style = element->GetComputedStyle();
  const ComputedStyle* style = GetDocument().GetStyleResolver().ResolveStyle(
      element, style_recalc_context);
  EXPECT_FALSE(style->HasCurrentBackgroundColorAnimation());

  NonThrowableExceptionState exception_state;
  DocumentTimeline* timeline =
      MakeGarbageCollected<DocumentTimeline>(&GetDocument());
  Animation* animation = Animation::Create(
      MakeGarbageCollected<KeyframeEffect>(element, model, timing), timeline,
      exception_state);
  animation->play();
  ASSERT_TRUE(element->GetElementAnimations());
  EXPECT_EQ(element->GetElementAnimations()->Animations().size(), 1u);
  GetDocument().View()->UpdateLifecycleToCompositingInputsClean(
      DocumentUpdateReason::kTest);
  EXPECT_TRUE(element->GetLayoutObject()->ShouldDoFullPaintInvalidation());
  EXPECT_TRUE(element->ComputedStyleRef().HasCurrentBackgroundColorAnimation());
  UpdateAllLifecyclePhasesForTest();

  start_keyframe->SetCSSPropertyValue(
      property_id, "red", SecureContextMode::kInsecureContext, nullptr);
  end_keyframe->SetCSSPropertyValue(
      property_id, "yellow", SecureContextMode::kInsecureContext, nullptr);
  keyframes.clear();
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);
  To<KeyframeEffect>(animation->effect())->SetKeyframes(keyframes);

  ASSERT_TRUE(element->GetElementAnimations());
  EXPECT_EQ(element->GetElementAnimations()->Animations().size(), 1u);
  GetDocument().View()->UpdateLifecycleToCompositingInputsClean(
      DocumentUpdateReason::kTest);
  EXPECT_TRUE(element->GetLayoutObject()->ShouldDoFullPaintInvalidation());
  EXPECT_TRUE(element->ComputedStyleRef().HasCurrentBackgroundColorAnimation());
}

TEST_F(BackgroundColorPaintDefinitionTest, TriggerRepaintNewStartTime) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  SetBodyInnerHTML("<div id=target></div>");

  Element* element = GetElementById("target");
  ASSERT_TRUE(element);

  Timing timing;
  timing.iteration_duration = ANIMATION_TIME_DELTA_FROM_SECONDS(30);

  CSSPropertyID property_id = CSSPropertyID::kBackgroundColor;
  Persistent<StringKeyframe> start_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  start_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);
  Persistent<StringKeyframe> end_keyframe =
      MakeGarbageCollected<StringKeyframe>();
  end_keyframe->SetCSSPropertyValue(
      property_id, "green", SecureContextMode::kInsecureContext, nullptr);

  StringKeyframeVector keyframes;
  keyframes.push_back(start_keyframe);
  keyframes.push_back(end_keyframe);
  auto* model = MakeGarbageCollected<StringKeyframeEffectModel>(keyframes);
  auto* effect = MakeGarbageCollected<KeyframeEffect>(element, model, timing);

  auto* animation =
      Animation::Create(effect, &GetDocument().Timeline(), ASSERT_NO_EXCEPTION);

  animation->play();

  GetDocument().View()->UpdateLifecycleToCompositingInputsClean(
      DocumentUpdateReason::kTest);
  EXPECT_TRUE(element->GetLayoutObject()->ShouldDoFullPaintInvalidation());
  EXPECT_TRUE(element->ComputedStyleRef().HasCurrentBackgroundColorAnimation());
  UpdateAllLifecyclePhasesForTest();

  // Set compositor pending.
  animation->setStartTime(MakeGarbageCollected<V8CSSNumberish>(0.5),
                          ASSERT_NO_EXCEPTION);
  EXPECT_TRUE(animation->CompositorPending());
  GetDocument().View()->UpdateLifecycleToCompositingInputsClean(
      DocumentUpdateReason::kTest);
  EXPECT_TRUE(element->GetLayoutObject()->ShouldDoFullPaintInvalidation());
  EXPECT_TRUE(element->ComputedStyleRef().HasCurrentBackgroundColorAnimation());
  ASSERT_TRUE(element->GetLayoutObject());

  UpdateAllLifecyclePhasesForTest();
  EXPECT_FALSE(animation->CompositorPending());
}

// Test that calling BackgroundColorPaintDefinition::Paint won't crash
// when the animated property value is empty.
TEST_F(BackgroundColorPaintDefinitionTest,
       ProxyClientPaintWithNoPropertyValue) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  Vector<Color> animated_colors = {Color(0, 255, 0), Color(255, 0, 0)};
  Vector<double> offsets = {0, 1};
  CompositorPaintWorkletJob::AnimatedPropertyValues property_values;
  RunPaintForTest(animated_colors, offsets, property_values);
}

// Test that BackgroundColorPaintDefinition::Paint won't crash if the
// progress of the animation is a negative number.
TEST_F(BackgroundColorPaintDefinitionTest,
       ProxyClientPaintWithNegativeProgress) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  Vector<Color> animated_colors = {Color(0, 255, 0), Color(255, 0, 0)};
  Vector<double> offsets = {0, 1};
  CompositorPaintWorkletJob::AnimatedPropertyValues property_values;
  CompositorPaintWorkletInput::PropertyKey property_key(
      CompositorPaintWorkletInput::NativePropertyType::kBackgroundColor,
      CompositorElementId(1u));
  CompositorPaintWorkletInput::PropertyValue property_value(-0.0f);
  property_values.insert(std::make_pair(property_key, property_value));
  RunPaintForTest(animated_colors, offsets, property_values);
}

// Test that BackgroundColorPaintDefinition::Paint won't crash if the
// progress of the animation is > 1.
TEST_F(BackgroundColorPaintDefinitionTest,
       ProxyClientPaintWithLargerThanOneProgress) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  Vector<Color> animated_colors = {Color(0, 255, 0), Color(255, 0, 0)};
  Vector<double> offsets = {0, 1};
  CompositorPaintWorkletJob::AnimatedPropertyValues property_values;
  CompositorPaintWorkletInput::PropertyKey property_key(
      CompositorPaintWorkletInput::NativePropertyType::kBackgroundColor,
      CompositorElementId(1u));
  float progress = 1 + std::numeric_limits<float>::epsilon();
  CompositorPaintWorkletInput::PropertyValue property_value(progress);
  property_values.insert(std::make_pair(property_key, property_value));
  RunPaintForTest(animated_colors, offsets, property_values);
}

// Test that BackgroundColorPaintDefinition::Paint won't crash when the
// largest offset is not exactly one.
TEST_F(BackgroundColorPaintDefinitionTest,
       ProxyClientPaintWithCloseToOneOffset) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  Vector<Color> animated_colors = {Color(0, 255, 0), Color(0, 255, 255),
                                   Color(255, 0, 0)};
  Vector<double> offsets = {0, 0.6, 0.99999};
  CompositorPaintWorkletJob::AnimatedPropertyValues property_values;
  CompositorPaintWorkletInput::PropertyKey property_key(
      CompositorPaintWorkletInput::NativePropertyType::kBackgroundColor,
      CompositorElementId(1u));
  float progress = 1 - std::numeric_limits<float>::epsilon();
  CompositorPaintWorkletInput::PropertyValue property_value(progress);
  property_values.insert(std::make_pair(property_key, property_value));
  RunPaintForTest(animated_colors, offsets, property_values);
}

// Test that BackgroundColorPaintDefinition::Paint handles colors with
// differing color spaces - i.e won't crash/DCHECK.
TEST_F(BackgroundColorPaintDefinitionTest,
       ProxyClientPaintWithColorOfDifferingColorSpaces) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  Vector<Color> animated_colors = {
      Color::FromColorSpace(Color::ColorSpace::kSRGBLegacy, 1, 0, 0, 1),
      Color::FromColorSpace(Color::ColorSpace::kSRGB, 0, 0.5, 0, 1),
  };
  Vector<double> offsets = {0, 1};
  CompositorPaintWorkletJob::AnimatedPropertyValues property_values;
  CompositorPaintWorkletInput::PropertyKey property_key(
      CompositorPaintWorkletInput::NativePropertyType::kBackgroundColor,
      CompositorElementId(1u));
  float progress = 0.5f;
  CompositorPaintWorkletInput::PropertyValue property_value(progress);
  property_values.insert(std::make_pair(property_key, property_value));
  RunPaintForTest(animated_colors, offsets, property_values);
}

// Test that BackgroundColorPaintDefinition::Paint handles colors with
// differing color spaces - i.e won't crash/DCHECK.
TEST_F(BackgroundColorPaintDefinitionTest,
       ProxyClientPaintWithColorOfDifferingColorSpacesReverse) {
  ScopedCompositeBGColorAnimationForTest composite_bgcolor_animation(true);
  Vector<Color> animated_colors = {
      Color::FromColorSpace(Color::ColorSpace::kSRGB, 1, 0, 0, 1),
      Color::FromColorSpace(Color::ColorSpace::kSRGBLegacy, 0, 0.5, 0, 1),
  };
  Vector<double> offsets = {0, 1};
  CompositorPaintWorkletJob::AnimatedPropertyValues property_values;
  CompositorPaintWorkletInput::PropertyKey property_key(
      CompositorPaintWorkletInput::NativePropertyType::kBackgroundColor,
      CompositorElementId(1u));
  float progress = 0.5f;
  CompositorPaintWorkletInput::PropertyValue property_value(progress);
  property_values.insert(std::make_pair(property_key, property_value));
  RunPaintForTest(animated_colors, offsets, property_values);
}

}  // namespace blink
