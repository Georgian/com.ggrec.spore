package com.ggrec.spore;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.base.Preconditions.checkArgument;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.ggrec.spore.Spore.ISporable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class SporeParser
{

	final private Iterator<Spore> itrEnclosedSpores;


	public SporeParser(final Spore spore)
	{
		this.itrEnclosedSpores = spore.iterator();
	}


	final public boolean hasNext()
	{
		return itrEnclosedSpores.hasNext();
	}


	final public <T> T nextAs_FromString(final Function<String, T> unfreezer)
	{
		final String nextPayload_ToString = nextAs_FromSpore(Spore::toString);
		return nextPayload_ToString == null ? null : unfreezer.apply(nextPayload_ToString);
	}


	final public <T> T nextAs_FromSpore(final Function<Spore, T> unfreezerFromSpore)
	{
		final Spore nextSpore = nextAsSpore();
		return nextSpore.isPayloadNull() ? null : unfreezerFromSpore.apply(nextSpore);
	}
	
	
	/**
	 * ggrec, 2017-07-20: The absolute most convenient API to use, but the most "unsure". This find the correct
	 *                    class by searching by the unique identifier found in the spore, then creates the instance
	 *                    of that class, then populates it.
	 */
	final public <T> T nextAs_FromSpore_Automatic()
	{
		return nextAs_FromSpore(Spore::toInstance);
	}
	
	
	private Optional<Stream<Spore>> nextAsSporeStream()
	{
		final Spore nextSpore = nextAsSpore();

		if (nextSpore.isPayloadNull())
			return Optional.empty();

		if (Spore.EMPTY_COLLECTION_PAYLOAD.equals(nextSpore.toString()))
			return Optional.of(Stream.of());

		// Remember, each collection is another spore. So we need to parse it
		final SporeParser nextSporeParser = new SporeParser(nextSpore);

		final Iterable<Spore> iterable = () -> nextSporeParser.itrEnclosedSpores;

		// Create the stream from the collection spore, and map unfreeze each element
		return Optional.ofNullable( StreamSupport.stream(iterable.spliterator(), false) );
	}
	
	
	final public <T> Stream<T> nextAsStream(final Function<Spore, T> unfreezer)
	{
		return nextAsSporeStream().map(stream -> stream.map(unfreezer)).orElse(Stream.empty());
	}
	
	
	/**
	 * A collection is a spore in itself, so in this method we're going to walk those inner elements 
	 * of the collection, and unfreeze them. 
	 * 
	 * @return NULL if the appended collection was NULL in the first place
	 */
	final public <T> ImmutableList<T> nextAsList(final Function<Spore, T> unfreezer)
	{
		return nextAsCollection(unfreezer, toImmutableList());
	}
	
	
	final public <T> ImmutableSet<T> nextAsImmutableSet(final Function<Spore, T> unfreezer)
	{
		return nextAsCollection(unfreezer, toImmutableSet());
	}
	
	
	/**
	 * TODO-ggrec, 2017-03-01: Would be nice to have a collector, so we can specify what map instance we want in the end 
	 */
	final public <K, V> Map<K, V> nextAsMap(final Function<Spore, K> keyUnfreezer, final Function<Spore, V> valUnfreezer)
	{
		return nextAsSporeStream().map(sporeStream -> {

			final Map<K, V> result = new HashMap<>();

			/*
			 * Map values and keys are stored in a linear fashion in the spore. The enclosed spores are
			 * parsed two by two, each pair being a map entry.
			 */
			final ImmutableList<Spore> sporeList = sporeStream.collect(toImmutableList());
			if (sporeList.size() >= 2)
			{
				checkArgument(sporeList.size() % 2 == 0, "You are not storing the map correctly"); //$NON-NLS-1$

				for (int i = 0; i < sporeList.size() - 1; i += 2)
				{
					final Spore sporeKey = sporeList.get(i);
					final Spore sporeVal = sporeList.get(i + 1);

					result.put(
							sporeKey.isPayloadNull() ? null : keyUnfreezer.apply(sporeKey), 
							sporeVal.isPayloadNull() ? null : valUnfreezer.apply(sporeVal));
				}
			}
					
			return result;
			
		}).orElse(null);
	}

	
	/**
	 * Risky API, use only if you know what you're doing.
	 * 
	 * Example: unfreezing of a Price Curves spore, where a UUID happens to be deleted from the universe, so the matching
	 *          instance wouldn't be found by the unfreezer
	 */
	final public <T, R> R nextAsCollection_ExcludeNullElements(final Function<Spore, T> unfreezer, final Collector<? super T, ?, R> collector)
	{
		return nextAsSporeStream().map(stream -> stream.map(unfreezer).filter(Objects::nonNull).collect(collector)).orElse(null);
	}
	

	/**
	 * A collection is a spore in itself, so in this method we're going to walk those inner elements 
	 * of the collection, and unfreeze them. 
	 * 
	 * @return NULL if the appended collection was NULL in the first place
	 */
	final public <T, R> R nextAsCollection(final Function<Spore, T> unfreezer, final Collector<? super T, ?, R> collector)
	{
		return nextAsSporeStream().map(stream -> stream.map(unfreezer).collect(collector)).orElse(null);
	}
	
	
	/**
	 * Populates the sporable using the concrete implementation of {@link ISporable#populateFromSpore(Spore)}
	 */
	final public <W extends ISporable> SporeParser parseNextSporeIntoSporable(final W freezable)
	{
		// The implementation should know how to handle NULL payloads of the spore !!!
		// You should know what you put in the spore in the first place !!
		freezable.populateFromSpore(nextAsSpore());
		return this;
	}


	/**
	 * If the next spore has a payload, a sporable instance will be created using the parameter, then populated by the API implementation 
	 */
	final public <W extends ISporable> W parseNextSporeAsSporable(final Supplier<W> sporableCreator)
	{
		return parseNextSporeAsSporable(version -> sporableCreator.get());
	}


	/**
	 * If the next spore has a payload, a sporable instance will be created using the parameter, then populated by the API implementation 
	 */
	@SuppressWarnings("unchecked")
	final public <W extends ISporable> W parseNextSporeAsSporable(final Function<String, W> sporableInstantiator_FromVersion)
	{
		final Spore nextSpore = nextAsSpore();
		return nextSpore.isPayloadNull() ? null : (W) checkNotNull(sporableInstantiator_FromVersion.apply(nextSpore.version()), "Must supply a sporable instance!").populateFromSpore(nextSpore); //$NON-NLS-1$
	}


	/**
	 * @return NULL if no next spore present
	 */
	final public SporeParser nextParser_IfPresent()
	{
		return hasNext() ? new SporeParser(nextAsSpore()) : null;
	}


	final public Spore nextAsSpore()
	{
		return itrEnclosedSpores.next();
	}


	final public String nextAsString()
	{
		return nextAs_FromString(Function.identity());
	}
	

	final public Integer nextAsInteger()
	{
		return nextAs_FromString(Integer::valueOf);
	}
	
	
	final public Double nextAsDouble()
	{
		return nextAs_FromString(Double::valueOf);
	}


	final public Boolean nextAsBool()
	{
		return nextAs_FromString(Boolean::parseBoolean);
	}


	final public Locale nextAsLocale()
	{
		return nextAs_FromString(Locale::forLanguageTag);
	}


	final public Long nextAsLong()
	{
		return nextAs_FromString(Long::valueOf);
	}


	final public UUID nextAsUUID()
	{
		return nextAs_FromString(UUID::fromString);
	}


	final public LocalDate nextAsDate()
	{
		return nextAs_FromString(spore -> LocalDate.parse(spore));
	}


	/**
	 * Returns an enum based on its stored ordinal.
	 */
	final public <E extends Enum<E>> E nextAsEnum(final Class<E> enumClass)
	{
		final Integer next = nextAsInteger();
		if (next != null)
			return enumClass.getEnumConstants()[next];

		return null;
	}

}