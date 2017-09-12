package com.ggrec.spore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.Test;

import com.ggrec.spore.Spore.ISporable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * 
 * @author GGrec
 *
 */
@SuppressWarnings("nls")
public class Spore_ModelTest
{

	@Test
	public void isPayloadNull() throws Exception
	{
		Spore spore = Spore.from((Object)null);
		assertThat(spore.isPayloadNull()).isTrue();
		
		spore = Spore.from(Optional.empty());
		assertThat(spore.isPayloadNull()).isTrue();
		
		spore = Spore.fromFrozenSpore(null);
		assertThat(spore.isPayloadNull()).isTrue();

		// We're appending a NULL object
		spore = new SporeBuilder().append( (Object) null ).build();
		assertThat(new SporeParser(spore).nextAsSpore().isPayloadNull()).isTrue();

		// We're appending a NULL collection
		spore = new SporeBuilder().appendAsCollection(null).build();
		assertThat(new SporeParser(spore).nextAsSpore().isPayloadNull()).isTrue();

		// We're appending an EMPTY collection, so that shouldn't be a NULL payload
		spore = new SporeBuilder().appendAsCollection(ImmutableList.of()).build();
		assertThat(new SporeParser(spore).nextAsSpore().isPayloadNull()).isFalse();
		
		// We're appending an collection of NULLs, so that shouldn't be a NULL payload
		List<Object> coll = new ArrayList<>(); coll.add(null);
		spore = new SporeBuilder().appendAsCollection(coll, Spore::from).build();
		assertThat(new SporeParser(spore).nextAsSpore().isPayloadNull()).isFalse();
		
		// We're appending an collection of NULLs, so that shouldn't be a NULL payload
		coll = new ArrayList<>(); coll.add(null); coll.add(null);
		spore = new SporeBuilder().appendAsCollection(coll, Spore::from).build();
		assertThat(new SporeParser(spore).nextAsSpore().isPayloadNull()).isFalse();
	}
	

	@Test
	public void nextAsString() throws Exception
	{
		final SporeBuilder sb = new SporeBuilder();

		sb.appendAsCollection(ImmutableList.of( new TestObj(""), new TestObj("")));
		sb.append("");
		sb.append(new TestObj("d"));
		sb.appendNullPayload();
		sb.append(UserStatus.PENDING);
		sb.append(null, null);
		sb.appendAsCollection(Arrays.asList());


		final Spore spore = sb.build();

		final SporeParser sp = new SporeParser(spore);
		final Spore sss = Spore.fromFrozenSpore( spore.toString() );
		System.out.println(sss);

		final Spore spore2 = sp.nextAsSpore();
		sb.append(new TestObj("").assembleSpore());
		new SporeParser(spore2);

		System.out.println(spore2);
	}


	@Test
	public void enclosedSpores_Unfrozen() throws Exception
	{
//		final SporeBuilder sb = new SporeBuilder();
//		final Spore spore = sb.build();
//		final SporeParser sp = new SporeParser(spore);
//
//		sp.nextAsCollection(sporee -> sporee.length());
	}


	@Test
	public void appendAsEnclosedSpores_NEW() throws Exception
	{
		final SporeBuilder builder = new SporeBuilder();

		// final ImmutableList<TestObj> objects = ImmutableList.of(new TestObj("One"), new TestObj("Two"), new TestObj("Three"));
		final ImmutableList<Integer> objects = ImmutableList.of(1, 2, 3);

		builder.appendAsCollection(objects, number -> Spore.from(number));

		System.out.println(builder.build());
	}


	@Test
	public void nextAsCollection() throws Exception
	{
		final SporeBuilder sb = new SporeBuilder();
		
		sb.appendAsCollection(null);
		sb.appendAsCollection(ImmutableList.of());
		sb.appendAsCollection(ImmutableList.of(new TestObj("Dan")));
		
		final Spore spore = sb.build();
		
		final SporeParser sp = new SporeParser(spore);
		final Function<Spore, Object> unfreezer = sporee -> new TestObj().populateFromSpore(sporee);

		assertThat(sp.nextAsList(unfreezer)).isNull();
		assertThat(sp.nextAsList(unfreezer)).isEmpty();
		assertThat(sp.nextAsList(unfreezer)).containsExactly(new TestObj("Dan"));
	}


	@Test
	public void nextAs_FromSpore() throws Exception
	{
		final SporeBuilder sb = new SporeBuilder();
		final Spore spore = sb.build();
		final SporeParser sp = new SporeParser(spore);
	}

	@Test
	public void fromString() throws Exception
	{
		final String frozen = "{|v001_|_u_|_FilterModelUniqueId_|_name_|_description_|_3245|}";

		final Spore spore = Spore.fromFrozenSpore(frozen);

		assertThat(spore.toString()).isEqualTo(frozen);
	}


	@Test
	public void fromStringIllegalArgument() throws Exception
	{
//		final SporeBuilder sb = new SporeBuilder();
//
//		final Spore sss = Spore.fromFrozenSpore("|}".toString());
	}


	@Test
	public void nextAs() throws Exception
	{
//		final SporeBuilder sb = new SporeBuilder();
//		final Spore spore = sb.build();
//		final SporeParser sp = new SporeParser(spore);
//		final Spore spore2 = sp.nextAsSpore();
//		final Boolean spore3 = sp.nextAsBool();
//		final Integer spore4 = sp.nextAsInteger();
//		final Locale spore5 = sp.nextAsLocale();
//		final AEFColor spore6 = sp.nextAsColor();
//		final String spore7 = sp.nextAsString();
//		sp.nextAsLong();
//		sp.nextAsEnum(UserStatus.class);


	}


	@Test
	public void next() throws Exception
	{
		final SporeBuilder sb = new SporeBuilder();
		final Spore spore = sb.build();
		final SporeParser sp = new SporeParser(spore.fromFrozenSpore(""));
		final Spore spore2 = sp.nextAsSpore();
		final Boolean spore3 = sp.nextAsBool();
	}


	@Test
	public void parseNextSporeIntoWrapper() throws Exception
	{
		final SporeBuilder sb = new SporeBuilder();
		final Spore spore = sb.build();
		final SporeParser sp = new SporeParser(spore);

	}


	@Test
	public void version() throws Exception
	{
		final SporeBuilder sb = new SporeBuilder();
		final Spore spore = sb.build();

	}

	@Test
	public void append() throws Exception
	{
		final SporeBuilder sb = new SporeBuilder();

		sb.append((String) null);
	}
	
	
	@Test
	public void appendAsMap() throws Exception
	{
		final Map<Integer, String> subsidiaryIds = new HashMap<>();
		final SporeBuilder sb = new SporeBuilder();
		
		sb.appendAsMap(null, Spore::from, Spore::from);
		sb.appendAsMap(ImmutableMap.of(), Spore::from, Spore::from);
		sb.appendAsMap(subsidiaryIds, Spore::from, Spore::from);
		
		final Spore spore = sb.build();
		final SporeParser sp = new SporeParser(spore);
		final Function<Spore, Object> unfreezer = sporee -> new TestObj().populateFromSpore(sporee);
		
		assertThat(sp.nextAsMap(unfreezer , Spore::from)).isNull();
		assertThat(sp.nextAsMap(unfreezer, Spore::from)).isEqualTo(ImmutableMap.of());
		assertThat(sp.nextAsMap(unfreezer, Spore::from)).isEqualTo(subsidiaryIds);
	}


	private static class TestObj implements ISporable
	{

		private String name;

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}
		@Override
		public boolean equals(final Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final TestObj other = (TestObj) obj;
			if (name == null)
			{
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
		private TestObj()
		{
			
		}
		public TestObj(final String name)
		{
			this.name = name;
		}

		@Override
		public SporeBuilder assembleSpore()
		{
			return new SporeBuilder(name)
					.append(name);
		}
		
		
		@Override
		public ISporable populateFromSpore(final Spore spore)
		{
			final SporeParser parser = new SporeParser(spore);
			name = parser.nextAsString();
			return this;
		}
	}

	
	private enum UserStatus 
	{
		PENDING,
		ACTIVE,
		INACTIVE,
		DELETED;
	}

}
