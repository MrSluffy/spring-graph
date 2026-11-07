/**
 * Implementation of the processor. <b>Not an API</b>: classes here are public only so the subpackages can use each other,
 * and they may change or disappear in any release without notice.
 *
 * <p>{@link io.springgraph.processor.internal.GraphAnalysis} runs the pipeline; the subpackages hold its steps:
 * {@code config} (options), {@code discovery} (components, SG004/SG005), {@code resolution} (dependency rules,
 * SG006/SG008), {@code findings} (SG001/SG003/SG007/SG009), {@code report} (report text), with {@code model} and
 * {@code diagnostic} shared by all of them.
 */
package io.springgraph.processor.internal;
