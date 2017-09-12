package com.ggrec.spore;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import com.ggrec.spore.Spore.CompositeSpore;
import com.ggrec.spore.Spore.ISporable;
import com.ggrec.spore.Spore.Sporable;
import com.ggrec.spore.Spore.SporeMetadata;
import com.ggrec.spore.Spore.SporeMetadataType;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * 
 * @author GGrec
 *
 */
public final class SporeBuilder
{

	final private Map<SporeMetadataType, String> metadataMap = new TreeMap<>();

	final private List<Spore> enclosedSpores = new ArrayList<>();


	public static <T extends ISporable> SporeBuilder on(final Class<T> sporable)
	{
		return new SporeBuilder(sporable);
	}


	public SporeBuilder(final Class<? extends ISporable> sporableClass)
	{
		checkNotNull(sporableClass, "Must provide a sporable instance"); //$NON-NLS-1$

		final Sporable ann = checkNotNull( sporableClass.getAnnotation(Sporable.class), 
				"This constructor of the %s requires the %s class to have the %s annotation", SporeBuilder.class.getSimpleName(), sporableClass.getSimpleName(), Sporable.class.getSimpleName() ); //$NON-NLS-1$

		final String version = ann.version();
		this.version( Strings.isNullOrEmpty(version) || Spore.NO_VERSION.equals(version) ? null : version );

		final String uniqueIdentifier = ann.uniqueIdentifier();
		this.uniqueIdentifier( Strings.isNullOrEmpty(uniqueIdentifier) || Spore.NO_UNIQUE_IDENTIFIER.equals(uniqueIdentifier) ? null : uniqueIdentifier );
	}


	/**
	 * Usually the spores without version are appended collections
	 */
	public SporeBuilder()
	{

	}


	public SporeBuilder(final String version)
	{
		version(version);
	}


	public SporeBuilder version(final String version)
	{
		metadataMap.put(SporeMetadataType.VERSION, version);
		return this;
	}


	public SporeBuilder uniqueIdentifier(final String uniqueIdentifier)
	{
		metadataMap.put(SporeMetadataType.UNIQUE_IDENTIFIER, uniqueIdentifier);
		return this;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("sporeCount", enclosedSpores.size()) //$NON-NLS-1$
				.toString();
	}


	public SporeBuilder appendAsEmptyCollection()
	{
		return append(Optional.of(Spore.EMPTY_COLLECTION_PAYLOAD));
	}


	public SporeBuilder appendNullPayload()
	{
		return append(Optional.empty());
	}


	/**
	 * This is very generic because it should work with all objects.
	 * The API is not defensive at all. When you use this, you really should have some tests in place. 
	 */
	public <T> SporeBuilder append(final T payload)
	{
		return append(Optional.ofNullable(payload));
	}


	/**
	 * This is very generic because it should work with all objects.
	 * The API is not defensive at all. When you use this, you really should have some tests in place. 
	 */
	public <T> SporeBuilder append(final Optional<T> payload_O)
	{
		/*
		 * ggrec, 2017-02-01: Special enum cases, which are always tricky
		 */
		if (payload_O.isPresent())
		{
			final T payload = payload_O.get();

			// Special cases first
			if (payload instanceof Enum<?>)
				return append(((Enum<?>) payload).ordinal());
		}

		return append(Spore.from(payload_O));
	}


	public <T> SporeBuilder append(final T payload, final Function<T, Spore> freezer)
	{
		return payload == null ? appendNullPayload() : append(freezer.apply(payload));
	}


	public <K, V> SporeBuilder appendAsMap(final Map<K, V> map, final Function<K, Spore> keyFreezer, final Function<V, Spore> valFreezer)
	{
		if (map != null)
			if (!map.isEmpty())
			{
				final ImmutableList.Builder<Spore> keyValSpores = ImmutableList.builder();
				map.forEach((k, v) -> { keyValSpores.add(keyFreezer.apply(k)); keyValSpores.add(valFreezer.apply(v)); });
				appendAsCollection(keyValSpores.build(), Function.identity());
			}
			else
				appendAsEmptyCollection();
		else
			appendNullPayload();


		return this;
	}


	public <T extends ISporable> SporeBuilder appendAsCollection(final Collection<T> collection)
	{
		return appendAsCollection(collection, Spore::from);
	}


	public <T extends ISporable> SporeBuilder appendAsStream(final Stream<T> stream)
	{
		return appendAsStream(stream, Spore::from);
	}


	/**
	 * PERFORMS TERMINAL OPERATIONS ON THE STREAM 
	 */
	public <T> SporeBuilder appendAsStream(final Stream<T> stream, final Function<T, Spore> freezer)
	{
		if (stream == null)
			return appendNullPayload();

		// if (stream.isEmpty())
		//     return appendAsEmptyCollection();

		final SporeBuilder sporeB = new SporeBuilder();

		// Map each entity to a spore, given by the freezer function. At this point, we assume
		// that the spores in theirselves are valid, and don't contain 'illegal' characters
		final long streamSize = stream
				.sequential()
				.map(freezer)
				.peek(spore -> {

					if (spore == null)
						sporeB.appendNullPayload();
					else 
						sporeB.append(spore);
				})
				.count();

		// Since there's no way of knowing if a stream is empty or not, we do this little hack with the counter
		return streamSize == 0 ? appendAsEmptyCollection() : append(sporeB);
	}


	public <T> SporeBuilder appendAsCollection(final Collection<T> collection, final Function<T, Spore> freezer)
	{
		return collection == null ? appendNullPayload() : appendAsStream(collection.stream(), freezer);

		//			if (collection == null)
		//				return appendNullPayload();
		//
		//			if (collection.isEmpty())
		//				return appendAsEmptyCollection();
		//
		//			final SporeBuilder sporeB = new SporeBuilder();
		//
		//			// Map each entity to a spore, given by the freezer function. At this point, we assume
		//			// that the spores in theirselves are valid, and don't contain 'illegal' characters
		//			collection.stream()
		//			.map(freezer)
		//			.forEach(spore -> {
		//				
		//				if (spore == null)
		//					sporeB.appendNullPayload();
		//				else 
		//					sporeB.append(spore);
		//			});
		//
		//			return append(sporeB);
	}



	public SporeBuilder append(final SporeBuilder enclosedSporeB)
	{
		checkNotNull(enclosedSporeB); 
		return append(enclosedSporeB.build());
	}


	public SporeBuilder append(final Spore enclosedSpore)
	{
		checkNotNull(enclosedSpore); 
		this.enclosedSpores.add(enclosedSpore);
		return this;
	}


	private SporeMetadata buildMetadata()
	{
		return !metadataMap.isEmpty() ? new SporeMetadata(metadataMap) : null;
	}


	public Spore build()
	{
		return new CompositeSpore( buildMetadata(), ImmutableList.copyOf(enclosedSpores) );
	}

}
