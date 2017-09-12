package com.ggrec.spore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.joining;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * ggrec, 2016-12-16: Implements Iterable because you move through the enclosed spores in a sequential fashion
 * 
 * ggrec, 2017-02-01: WYSIWYG. You HAVE to know what you put in the Builder, in order to know what's coming out from the Parser.
 *  
 * ggrec, 2017-02-01: No use for a NULL spore, because internally it would be the same thing a using the NULL_PAYLOAD constant
 *                    Remember that ultimately, the spore object will be a string. 
 *                    
 * ggrec, 2017-02-13: Second version of the spore framework is now working. This includes a metadata section where you can add all 
 *                    kinds of stuff like version and unique ID. Often in the code you'll see this as "version 3", because the
 *                    very first version was modeled with SporeHelper
 * 
 * @author ggrec
 *
 */
abstract public class Spore implements Iterable<Spore>
{

	// ==================== 1. Static Fields ========================

	static final String NULL_PAYLOAD = "--"; 
	static final String EMPTY_COLLECTION_PAYLOAD = "-e-"; //$NON-NLS-1$

	static final String NO_VERSION = "NO_VERSION"; //$NON-NLS-1$
	static final String NO_UNIQUE_IDENTIFIER = "NO_UNIQUE_IDENTIFIER"; //$NON-NLS-1$

	private static final String SPORE_PREFIX = "{|"; //$NON-NLS-1$
	private static final String SPORE_SUFFIX = "|}"; //$NON-NLS-1$
	private static final String SPORE_MEMBER_SEPARATOR = "_|_"; //$NON-NLS-1$


	// ==================== 3. Static Methods ====================

	public static boolean isStringASpore(final String string)
	{
		return string != null && string.startsWith(SPORE_PREFIX) && string.endsWith(SPORE_SUFFIX);
	}


	public static <T> Spore from(final Optional<T> object_O)
	{
		return checkNotNull(object_O, "Null not supported here") //$NON-NLS-1$

				// 1. Check if the object is a sporable instance. If so, we just build its spore
				.filter(ISporable.class::isInstance)
				.map(ISporable.class::cast)
				.map(sporableObj -> sporableObj.assembleSpore().build())

				// ggrec, 2017-03-01: If you use orElse, the first atomic spore which you see down here will be created, although it isn't necessary
				.orElseGet(() -> object_O

						// 2. If the object is not a sporable instance, we create an atomic spore of its toString
						.map(obj -> new AtomicSpore(obj.toString()))

						// 3. If all else fails, it means the object is actually NULL, so we create a NULL payload
						.orElseGet(() -> new AtomicSpore(NULL_PAYLOAD))

						);
	}


	public static <T> Spore from(final T object)
	{
		return from(Optional.ofNullable(object));
	}


	public static Spore fromFrozenSpore(final String frozenSpore)
	{
		// If the frozen spore is NULL, or if it isn't a composite spore, then we use the simple atomic API
		if (frozenSpore == null || (!frozenSpore.startsWith(SPORE_PREFIX) && !frozenSpore.endsWith(SPORE_SUFFIX)))
			return from(frozenSpore);

		// Composite 
		if (isStringASpore(frozenSpore))
		{
			final String frozenSpore_NoLimits = trimSporeLimits(frozenSpore);

			final ImmutableList<String> members = parseTopLevel(frozenSpore_NoLimits);

			SporeMetadata metadata = null;
			ImmutableList<Spore> enclosedSpores = null;

			if (!members.isEmpty())
			{
				// This will be NULL if the first member isn't a metadata object
				metadata = SporeMetadata.fromFrozenSpore(members.get(0));

				enclosedSpores = members.subList(metadata == null ? 0 : 1, members.size()).stream()
						.map(member -> fromFrozenSpore(member))
						.collect(toImmutableList());
			}

			return new CompositeSpore(metadata, enclosedSpores == null ? ImmutableList.of() : enclosedSpores);
		}

		else
			throw new IllegalArgumentException(MessageFormat.format("Unknown spore format: {0}", frozenSpore)); //$NON-NLS-1$
	}


	private static String trimSporeLimits(final String frozenSpore)
	{
		return frozenSpore.substring(SPORE_PREFIX.length(), frozenSpore.length() - SPORE_SUFFIX.length());
	}
	
	
	/**
	 * This API was created for Spores with collections in them, where the collection can either be NULL or EMPTY 
	 */
	public static boolean isSporeWithNullOrEmptyPayload(final Spore spore)
	{
		if (!spore.isPayloadNull())
		{
			final SporeParser parser = new SporeParser(spore);
			if (parser.hasNext())
			{
				final Spore enclosedSpore = parser.nextAsSpore();
				return enclosedSpore.isPayloadNull() || Objects.equals(enclosedSpore.toString(), EMPTY_COLLECTION_PAYLOAD);
			}
		}
		
		return true;
	}


	/**
	 * ggrec, 2016-12-21: Stolen from SporeHelper, no idea how this works. 
	 */
	private static ImmutableList<String> parseTopLevel(final String spore)
	{
		final ImmutableList.Builder<String> result = ImmutableList.builder();
		int lastSepIndex = 0;
		int openedBraces = 0;
		int closedBraces = 0;
		int nextSepIndex;

		for (int i = 0; i < spore.length(); i++)
		{
			final String substring = spore.substring(i);

			if (substring.startsWith(SPORE_PREFIX))
				openedBraces++;

			if (substring.startsWith(SPORE_SUFFIX))
				closedBraces++;

			if (substring.startsWith(SPORE_MEMBER_SEPARATOR))
			{
				nextSepIndex = i;

				if (openedBraces == closedBraces)
				{
					result.add(spore.substring(lastSepIndex, nextSepIndex));
					lastSepIndex = nextSepIndex + SPORE_MEMBER_SEPARATOR.length();
					openedBraces = closedBraces = 0;
					i = i + SPORE_MEMBER_SEPARATOR.length() - 1;
				}
			}
		}

		if (spore.contains(SPORE_MEMBER_SEPARATOR))
			result.add(spore.substring(lastSepIndex, spore.length()));
		else
			result.add(spore);

		return result.build();
	}


	public <T extends ISporable> T toInstance()
	{
		return toInstance(null);
	}
	

	public <T extends ISporable> T toInstance(final Class<? super T> superClass)
	{
		return toInstance(superClass, null);
	}
	
	
	/**
	 * This API gets the unique identifier from the spore metadata 
	 */
	public <T extends ISporable> T toInstance(final Class<? super T> superClass, final String byJavaFilename)
	{
		
		// --------------------- <Phase 1> -----------------------
		// Get the metadata from the spore

		final SporeMetadata metadata = checkNotNull(metadata(), 
				"No metadata present in the spore. Don't know which class are you trying to instantiate."); //$NON-NLS-1$


		// --------------------- <Phase 2> -----------------------
		// Make sure the metadata contains the unique identifier, which is mandatory for this operation

		final String uniqueIdentifier = checkNotNull(metadata.uniqueIdentifier(), 
				"Unique Identifier is missing from the spore. The class which built the spore must have that attribute set in the @Sporable annotation"); //$NON-NLS-1$
		
		
		return toInstance(uniqueIdentifier, superClass, byJavaFilename);
	}


	/**
	 * This instantiator is faster because it only searches throught the subclasses of the parameter 
	 */
	@SuppressWarnings("unchecked")
	public <T extends ISporable> T toInstance(final String uniqueIdentifier, final Class<? super T> superClass, final String byJavaFilename)
	{

		// --------------------- <Phase 6> -----------------------
		// Finally populate the new instance of this Spore. If there are any errors inside

		return (T) toInstance_WithoutPopulating(uniqueIdentifier, superClass, byJavaFilename).populateFromSpore(this);
	}
	
	
	@SuppressWarnings("unchecked")
	public <T extends ISporable> T toInstance_WithoutPopulating(final String uniqueIdentifier, final Class<? super T> superClass, final String byJavaFilename)
	{

		// --------------------- <Phase 3> -----------------------
		// Search for the class which has the Sporable annotation which has the uniqueIdentifier attribute set to the one which we extracted from the Spore

		final Class<?> clazz = AnnotationScanner.forAnnotationAndSuperClass(Sporable.class, superClass)
				.setByJavaFilename(byJavaFilename)
				.setAnnAttrFilter(ann -> Objects.equals(ann.uniqueIdentifier(), uniqueIdentifier))
				.scan()
				.orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("Could not find sporable class for identifier {0}", uniqueIdentifier))); //$NON-NLS-1$ 

		
		// --------------------- <Phase 4> -----------------------
		// Try to create the new instance from the class that was found

		Object newInstance = null;

		try
		{
			newInstance = checkNotNull( clazz.newInstance() );
		}
		catch (InstantiationException | IllegalAccessException | NullPointerException ex)
		{
			throw new IllegalArgumentException(MessageFormat.format("Could not instantiate class of type {0}", clazz.getSimpleName()), ex);
		}


		// --------------------- <Phase 5> -----------------------
		// That object which we found should be an ISporable instance, in order to populate it

		T newSporableInstance = null;

		try
		{
			newSporableInstance = checkNotNull( (T) newInstance );
		}
		catch (final ClassCastException | NullPointerException ex)
		{
			throw new IllegalArgumentException(MessageFormat.format("Class of type {0} does not implement the ISporable interface", clazz.getSimpleName()), ex);
		}

		return newSporableInstance;
	}
	

	/**
	 * Used for when fields change in classes across time 
	 */
	abstract public String version();


	/**
	 * 2017-02-08: Automated mechanism for locating subclasses and instantiate them before populating with the parser.
	 *             Before this day, a spore version prefix was added to the version itself. You can now use this instead of that. 
	 */
	abstract public String uniqueIdentifier();


	abstract public SporeMetadata metadata();


	/**
	 * If you need to use this on the outside, you're probably doing something wrong.
	 * The Spore Parser methods should suffice to tell you what a spore contains. You should play with the spore itself. 
	 */
	abstract public boolean isPayloadNull();


	// =======================================================
	// 			 19. Inline Classes 
	// =======================================================

	public interface ISporable
	{
		SporeBuilder assembleSpore();
		default ISporable populateFromSpore(final Spore spore) { return this; }
	}


	@Retention(RUNTIME)
	@Target(TYPE)
	public @interface Sporable
	{
		String version() default NO_VERSION;
		String uniqueIdentifier() default NO_UNIQUE_IDENTIFIER;
	}


	private static final class AtomicSpore extends Spore
	{

		final private String payload;


		private AtomicSpore(final String payload)
		{
			this.payload = payload;
		}


		@Override
		public String toString()
		{
			return payload;
		}


		@Override
		public boolean isPayloadNull()
		{
			return NULL_PAYLOAD.equals(payload);
		}


		@Override
		public SporeMetadata metadata()
		{
			return null;
		}

		/**
		 * Atomic spores aren't versioned
		 */
		@Override
		public String version()
		{
			return null;
		}


		@Override
		public String uniqueIdentifier()
		{
			return null;
		}


		@Override
		public Iterator<Spore> iterator()
		{
			return new Iterator<Spore>()
			{
				@Override public Spore next()
				{
					return AtomicSpore.this;
				}

				@Override public boolean hasNext()
				{
					return false;
				}
			};
		}

	}


	final static class CompositeSpore extends Spore
	{

		final private ImmutableList<Spore> enclosedSpores;

		final private SporeMetadata metadata;


		CompositeSpore(final SporeMetadata metadata, final ImmutableList<Spore> enclosedSpores)
		{
			this.metadata = metadata;
			this.enclosedSpores = checkNotNull(enclosedSpores, "Null not supported here. If you want a NULL collection, use the NULL spore. This can only be empty."); //$NON-NLS-1$
		}


		@Override
		public SporeMetadata metadata()
		{
			return metadata;
		}


		/**
		 * Does NOT return NULL !
		 */
		@Override
		public String version()
		{
			return metadata() == null ? null :  metadata().version();
		}


		@Override
		public String uniqueIdentifier()
		{
			return metadata() == null ? null : metadata().uniqueIdentifier();
		}


		@Override
		public String toString()
		{
			Stream<Spore> sporeStream = enclosedSpores.stream();

			if (metadata() != null)
				sporeStream = Stream.concat(Stream.of(metadata().assembleSpore().build()), sporeStream);

			return SPORE_PREFIX + sporeStream.map(Object::toString).collect(joining(SPORE_MEMBER_SEPARATOR)) + SPORE_SUFFIX;
		}


		@Override
		public Iterator<Spore> iterator()
		{
			return enclosedSpores.iterator();
		}


		@Override
		public boolean isPayloadNull()
		{
			// This may not have a NULL payload. The payload of this spore is a collection, which may be empty, but not null !
			return false;
		}

	}


	enum SporeMetadataType
	{

		METADATA_PREFIX ("spr"), //$NON-NLS-1$

		VERSION ("v"), //$NON-NLS-1$ 

		UNIQUE_IDENTIFIER ("u") //$NON-NLS-1$ 

		;

		final private String prefix;

		private SporeMetadataType(final String prefix)
		{
			this.prefix = prefix;
		}

		protected String prefix()
		{
			return prefix;
		}
	}


	private final static class SporeMetadataEntry 
	{

		private String metadataInfo;

		private SporeMetadataType type;

		protected static SporeMetadataEntry metadataPrefix()
		{
			return new SporeMetadataEntry(SporeMetadataType.METADATA_PREFIX);
		}

		private SporeMetadataEntry(final SporeMetadataType type)
		{
			this(null, type);
		}


		private SporeMetadataEntry(final String metadataInfo, final SporeMetadataType type)
		{
			this.metadataInfo = Strings.isNullOrEmpty(metadataInfo) ? null : metadataInfo;
			this.type = checkNotNull(type);
		}

		protected String metadataInfo()
		{
			return metadataInfo;
		}

		protected SporeMetadataType type()
		{
			return type;
		}

		protected Spore toSpore()
		{
			return Spore.from(this);
		}

		protected static SporeMetadataEntry fromSpore(final Spore spore)
		{
			final String sporeAsString = spore.toString();

			final SporeMetadataType type = Arrays.stream(SporeMetadataType.values())
					.filter(val -> sporeAsString.startsWith(val.prefix()))
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("Cannot determine metadata type of {0}", sporeAsString))); //$NON-NLS-1$ 

			final String metadataInfo = sporeAsString.substring(type.prefix().length(), sporeAsString.length());

			return new SporeMetadataEntry(metadataInfo, type);
		}

		@Override
		public String toString()
		{
			final String result = type().prefix();
			return metadataInfo() == null ? result : result + metadataInfo();
		}

	}


	final static class SporeMetadata implements ISporable
	{

		private ImmutableList<SporeMetadataEntry> entries;


		private static SporeMetadata fromFrozenSpore(final String frozenMetadataSpore)
		{
			final Spore metadataSpore = Spore.fromFrozenSpore(frozenMetadataSpore);
			final SporeParser metadataSporeParser = new SporeParser(metadataSpore);

			// Metadata object is identified by the prefix
			// Metadata looks like this (2017-02-09): {|spr_|_version_|_uniqueIdentifier|}
			if (metadataSporeParser.hasNext() && SporeMetadataType.METADATA_PREFIX.prefix().equals(metadataSporeParser.nextAsString()))
				return new SporeMetadata().populateFromSpore(metadataSpore);

			return null;
		}


		private SporeMetadata()
		{
		}


		SporeMetadata(final Map<SporeMetadataType, String> metadataMap)
		{
			checkArgument(metadataMap != null && !metadataMap.isEmpty());

			this.entries = Stream.concat(Stream.of(SporeMetadataEntry.metadataPrefix()), metadataMap.keySet().stream().map(key -> new SporeMetadataEntry(metadataMap.get(key), key)))
					.collect(toImmutableList());
		}


		private SporeMetadataEntry entryForType(final SporeMetadataType type)
		{
			return this.entries.stream()
					.filter(entry -> entry.type().equals(type))
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No metadata of type {0}", type))); //$NON-NLS-1$ 
		}


		public String version()
		{
			return entryForType(SporeMetadataType.VERSION).metadataInfo();
		}


		public String uniqueIdentifier()
		{
			return entryForType(SporeMetadataType.UNIQUE_IDENTIFIER).metadataInfo();
		}


		@Override
		public SporeBuilder assembleSpore()
		{
			final SporeBuilder builder = new SporeBuilder();
			entries.forEach(entry -> builder.append(entry.toSpore()));
			return builder;
		}


		@Override
		public SporeMetadata populateFromSpore(final Spore spore)
		{
			final SporeParser parser = new SporeParser(spore);

			final ImmutableList.Builder<SporeMetadataEntry> entriesB = ImmutableList.builder();
			while (parser.hasNext())
				entriesB.add(parser.nextAs_FromSpore(SporeMetadataEntry::fromSpore));
			entries = entriesB.build();

			return this;
		}

	}

}
