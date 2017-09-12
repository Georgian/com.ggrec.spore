package com.ggrec.spore;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.reflections.Reflections;
import org.reflections.scanners.Scanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

/**
 * 
 * @author ggrec
 *
 */
final public class AnnotationScanner<A extends Annotation>
{

	// ====================== 2. Instance Fields =============================

	private Collection<URL> urlsToScan = ClasspathHelper.forPackage("com.ansis"); //$NON-NLS-1$

	private Class<A> byAnnotation;

	private Predicate<A> annotationFilter;

	private Class<?> bySubClassOfSuper;

	private String byJavaFilename;

	/**
	 * ggrec, 2017-02-09: Does not work in iREMS with Reflections 0.9.10, because of their fault
	 *                    https://github.com/ronmamo/reflections/issues/81
	 *                    
	 * ggrec, 2017-02-14: Using reflections-0.9.9, so we can enable parallel search by default
	 */
	private boolean useParallelSearch = true;


	// ==================== 3. Static Methods ====================

	public static <A extends Annotation> AnnotationScanner<A> forAnnotation(final Class<A> annotationClass)
	{
		return new AnnotationScanner<A>().setByAnnotation(annotationClass);
	}


	public static <A extends Annotation> AnnotationScanner<A> forAnnotationAndSuperClass(final Class<A> annotationClass, final Class<?> superClass)
	{
		return forAnnotation(annotationClass).setBySubClass(superClass);
	}


	// ==================== 5. Creators ====================

	private Scanner[] createScanners()
	{
		final List<Scanner> scanners = new ArrayList<>();

		if (bySubClassOfSuper != null)
			scanners.add(new SubTypesScanner());

		if (byAnnotation != null)
		{
			final TypeAnnotationsScanner typeAnnScanner = new TypeAnnotationsScanner();
			// ggrec, 2016-12-14: I know for a fact that the internal engine of the metadata adapter uses the #getTypeName
			//                    API to fetch the name from the annotation class. See JavassistAdapter#getAnnotationNames
			typeAnnScanner.filterResultsBy(Predicates.equalTo(byAnnotation.getTypeName()));

			scanners.add(typeAnnScanner);
		}

		return scanners.toArray(new Scanner[scanners.size()]);
	}


	private ConfigurationBuilder createConfig()
	{
		final ConfigurationBuilder config = new ConfigurationBuilder()

				// Search only for classes, which have annotations with the name below
				.setScanners(createScanners())

				// Location to search for the files. You can also use ClasspathHelper.forPackage("com.ansis")
				.addUrls(urlsToScan);

		if (!Strings.isNullOrEmpty(byJavaFilename))
			// Search only for files which have this keyword in their name
			config.filterInputsBy(input -> input.contains(byJavaFilename)); 

		if (useParallelSearch)
			config.useParallelExecutor();

		return config;
	}


	// ==================== 6. Action Methods ====================

	private Set<Class<?>> getTypes(final Reflections reflections)
	{
		if (bySubClassOfSuper != null)
			return ImmutableSet.copyOf( reflections.getSubTypesOf(bySubClassOfSuper) );

		else if (byAnnotation != null)
			return reflections.getTypesAnnotatedWith(byAnnotation, true);

		throw new UnsupportedOperationException("Mechanism doesn't know what class to search for!"); //$NON-NLS-1$
	}


	public synchronized Optional<Class<?>> scan()
	{
		final Reflections reflections = new Reflections(createConfig());

		final Set<Class<?>> result = getTypes(reflections);

		final ImmutableSet<Class<?>> filteredResult = result.parallelStream()
				.filter(filterAnnotations())
				.collect(ImmutableSet.toImmutableSet());

		if (filteredResult.isEmpty())
			return Optional.empty();

		else if (filteredResult.size() > 1)
			throw new IllegalArgumentException(MessageFormat.format("Found several identical {0} annotations after all the filtering", byAnnotation.getSimpleName()));

		else
			return Optional.of(filteredResult.iterator().next());
	}


	// ==================== 7. Getters & Setters ====================

	private final AnnotationScanner<A> setByAnnotation(final Class<A> byAnnotation)
	{
		this.byAnnotation = byAnnotation;
		return this;
	}


	private final AnnotationScanner<A> setBySubClass(final Class<?> bySubClass)
	{
		this.bySubClassOfSuper = bySubClass;
		return this;
	}


	public AnnotationScanner<A> setAnnAttrFilter(final Predicate<A> annotationFilter)
	{
		this.annotationFilter = annotationFilter;
		return this;
	}


	public AnnotationScanner<A> setByJavaFilename(final String filename)
	{
		this.byJavaFilename = filename;
		return this;
	}


	private Predicate<Class<?>> filterAnnotations()
	{
		return clazz -> {

			final A[] annotations = clazz.getAnnotationsByType(byAnnotation);

			checkArgument(annotations.length <= 1, "Class %s has several %s annotations", clazz.getSimpleName(), byAnnotation.getSimpleName()); //$NON-NLS-1$

			if (annotations.length == 1)
				return annotationFilter == null ? true : annotationFilter.test(annotations[0]);

			return false;
		};
	}



}
